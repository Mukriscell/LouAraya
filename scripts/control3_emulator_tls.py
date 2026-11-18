"""HTTPS bridge for testing the signed academic APK against local Spring services."""

import argparse
import http.client
import shutil
import ssl
import subprocess
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Event, Thread
from typing import Optional


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "output" / "control3"
PRIVATE = OUTPUT / "private" / "emulator-tls"
RESOURCES = OUTPUT / "emulator-tls-res"
MAX_BODY = 16 * 1024 * 1024
HOP_HEADERS = {
    "connection", "host", "keep-alive", "proxy-authenticate", "proxy-authorization",
    "proxy-connection", "te", "trailer", "transfer-encoding", "upgrade", "content-length",
}


def generate_certificates(directory: Path, openssl: str) -> None:
    def run(*args: str):
        subprocess.run([openssl, *args], cwd=directory, check=True, capture_output=True)

    run("req", "-new", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "365",
        "-subj", "/CN=LouAraya CA local de evaluacion", "-keyout", "ca-key.pem",
        "-out", "ca.pem", "-addext", "basicConstraints=critical,CA:TRUE",
        "-addext", "keyUsage=critical,keyCertSign,cRLSign")
    run("req", "-new", "-newkey", "rsa:2048", "-nodes", "-subj", "/CN=10.0.2.2",
        "-keyout", "server-key.pem", "-out", "server.csr",
        "-addext", "subjectAltName=IP:10.0.2.2,IP:127.0.0.1",
        "-addext", "extendedKeyUsage=serverAuth")
    run("x509", "-req", "-in", "server.csr", "-CA", "ca.pem", "-CAkey", "ca-key.pem",
        "-CAcreateserial", "-days", "365", "-copy_extensions", "copy", "-out", "server.pem")


def prepare_certificates(openssl: str, tls_directory: Optional[Path] = None) -> ssl.SSLContext:
    directory = tls_directory if tls_directory is not None else PRIVATE
    ca, server, server_key = (directory / filename for filename in ("ca.pem", "server.pem", "server-key.pem"))
    required = (ca, server, server_key)
    if tls_directory is None and not any(file.exists() for file in (*required, directory / "ca-key.pem")):
        directory.mkdir(parents=True, exist_ok=True)
        generate_certificates(directory, openssl)

    missing = [file.name for file in required if not file.is_file()]
    if missing:
        raise RuntimeError(f"Faltan archivos TLS en {directory}: {', '.join(missing)}")

    tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    tls.minimum_version = ssl.TLSVersion.TLSv1_2
    tls.load_cert_chain(server, server_key)

    for resource_type in ("raw", "xml"):
        (RESOURCES / resource_type).mkdir(parents=True, exist_ok=True)
    shutil.copyfile(ca, RESOURCES / "raw" / "control3_local_ca.pem")
    (RESOURCES / "xml" / "control3_network_policy.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<network-security-config>\n'
        '    <base-config cleartextTrafficPermitted="false" />\n'
        '    <domain-config cleartextTrafficPermitted="false">\n'
        '        <domain includeSubdomains="false">10.0.2.2</domain>\n'
        '        <trust-anchors><certificates src="@raw/control3_local_ca" /></trust-anchors>\n'
        '    </domain-config>\n'
        '</network-security-config>\n', encoding="utf-8",
    )
    return tls


def bridge_to(port: int):
    class Bridge(BaseHTTPRequestHandler):
        def forward(self):
            try:
                size = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                self.send_error(400)
                return
            if size < 0 or size > MAX_BODY:
                self.send_error(413)
                return
            if self.headers.get("Transfer-Encoding") or not self.path.startswith("/"):
                self.send_error(400)
                return
            payload = self.rfile.read(size) if size else None
            headers = {key: value for key, value in self.headers.items() if key.lower() not in HOP_HEADERS}
            connection = http.client.HTTPConnection("127.0.0.1", port, timeout=12)
            try:
                connection.request(self.command, self.path, body=payload, headers=headers)
                response = connection.getresponse()
                body = response.read(MAX_BODY + 1)
                if len(body) > MAX_BODY:
                    self.send_error(502)
                    return
                self.send_response(response.status)
                for key, value in response.getheaders():
                    if key.lower() not in HOP_HEADERS:
                        self.send_header(key, value)
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            except (OSError, http.client.HTTPException):
                self.send_error(502, "Servicio local no disponible")
            finally:
                connection.close()

        def log_request(self, code="-", size="-"):
            # Keep bearer tokens, credentials, and query strings out of the log.
            self.log_message("%s -> %s", self.command, code)

        do_GET = do_POST = do_PUT = do_PATCH = do_DELETE = forward

    return Bridge


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--openssl", default="openssl")
    parser.add_argument("--tls-directory", type=Path, help="directorio con ca.pem, server.pem y server-key.pem")
    parser.add_argument("--prepare-only", action="store_true")
    args = parser.parse_args()
    try:
        context = prepare_certificates(args.openssl, args.tls_directory)
    except (OSError, RuntimeError) as error:
        parser.exit(1, f"{error}\n")
    if args.prepare_only:
        print(RESOURCES)
        return

    bridges = []
    try:
        for tls_port, local_port in ((8441, 8081), (8442, 8082)):
            bridge = ThreadingHTTPServer(("127.0.0.1", tls_port), bridge_to(local_port))
            bridge.socket = context.wrap_socket(bridge.socket, server_side=True)
            bridges.append(bridge)
            Thread(target=bridge.serve_forever, daemon=True).start()
            print(f"Emulador https://10.0.2.2:{tls_port} -> servicio local {local_port}", flush=True)
        Event().wait()
    finally:
        for bridge in bridges:
            bridge.shutdown()
            bridge.server_close()


if __name__ == "__main__":
    main()
