import argparse
import base64
import binascii
import json
import os
import re
import sqlite3
from contextlib import closing
from hmac import compare_digest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit
from uuid import UUID


PASSWORD = os.getenv("LOUARAYA_PROTOTYPE_PASSWORD", "LouDemo2026!")
ACCOUNTS = {
    "cliente.demo": "CLIENT",
    "admin.demo": "ADMIN",
    "cliente2.demo": "CLIENT",
}
PRODUCTS = {
    1: {"id": 1, "name": "Tapiz Aurora", "price": 45000},
    2: {"id": 2, "name": "Colgante Brisa", "price": 25000},
}
DATABASE = Path(os.getenv("LOUARAYA_PROTOTYPE_DB", Path(__file__).resolve().parents[1] / "output" / "prototype" / "requests.sqlite3"))


def connect():
    db = sqlite3.connect(DATABASE)
    db.row_factory = sqlite3.Row
    return db


def initialize():
    DATABASE.parent.mkdir(parents=True, exist_ok=True)
    with closing(connect()) as db, db:
        db.execute("""
            CREATE TABLE IF NOT EXISTS requests (
                id INTEGER PRIMARY KEY,
                owner TEXT NOT NULL,
                request_key TEXT NOT NULL,
                product_id INTEGER NOT NULL,
                product TEXT NOT NULL,
                quantity INTEGER NOT NULL CHECK(quantity BETWEEN 1 AND 99),
                total INTEGER NOT NULL,
                status TEXT NOT NULL CHECK(status IN ('PENDING', 'CONFIRMED')),
                UNIQUE(owner, request_key)
            )
        """)


def request_view(row):
    return {name: row[name] for name in ("id", "owner", "product", "quantity", "total", "status")}


class RequestError(Exception):
    def __init__(self, code):
        self.code = code


class Handler(BaseHTTPRequestHandler):
    def respond(self, code, payload):
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def identity(self):
        try:
            scheme, encoded = self.headers.get("Authorization", "").split(" ", 1)
            if scheme.lower() != "basic":
                raise ValueError
            alias, password = base64.b64decode(encoded, validate=True).decode("utf-8").split(":", 1)
        except (ValueError, UnicodeError, binascii.Error):
            raise RequestError(401)
        role = ACCOUNTS.get(alias)
        if role is None or not compare_digest(password.encode("utf-8"), PASSWORD.encode("utf-8")):
            raise RequestError(401)
        return alias, role

    def read_draft(self):
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if not 0 < size <= 1024:
                raise ValueError
            data = json.loads(self.rfile.read(size))
            key = str(UUID(data["key"]))
            product_id, quantity = data["productId"], data["quantity"]
            if type(product_id) is not int or product_id not in PRODUCTS:
                raise ValueError
            if type(quantity) is not int or not 1 <= quantity <= 99:
                raise ValueError
        except (ValueError, TypeError, KeyError, AttributeError):
            raise RequestError(400)
        return key, PRODUCTS[product_id], quantity

    def do_GET(self):
        self.handle_api("GET")

    def do_POST(self):
        self.handle_api("POST")

    def handle_api(self, method):
        self.connection.settimeout(10)
        path = urlsplit(self.path).path
        try:
            alias, role = self.identity()
            with closing(connect()) as db, db:
                if method == "GET" and path == "/api/snapshot":
                    query = "SELECT * FROM requests ORDER BY id DESC"
                    rows = db.execute(query) if role == "ADMIN" else db.execute(
                        "SELECT * FROM requests WHERE owner = ? ORDER BY id DESC", (alias,)
                    )
                    code, payload = 200, {
                        "profile": {"alias": alias, "role": role},
                        "products": list(PRODUCTS.values()),
                        "requests": [request_view(row) for row in rows],
                    }
                elif method == "POST" and path == "/api/requests":
                    if role != "CLIENT":
                        raise RequestError(403)
                    code, payload = self.create_request(db, alias)
                elif method == "POST" and (match := re.fullmatch(r"/api/requests/([1-9][0-9]{0,17})/confirm", path)):
                    if role != "ADMIN":
                        raise RequestError(403)
                    code, payload = self.confirm_request(db, int(match[1]))
                else:
                    raise RequestError(404)
            self.respond(code, payload)
        except RequestError as error:
            self.respond(error.code, {"error": error.code})
        except TimeoutError:
            self.respond(408, {"error": 408})

    def create_request(self, db, alias):
        key, product, quantity = self.read_draft()
        result = db.execute("""
            INSERT INTO requests(owner, request_key, product_id, product, quantity, total, status)
            VALUES (?, ?, ?, ?, ?, ?, 'PENDING')
            ON CONFLICT(owner, request_key) DO NOTHING
        """, (alias, key, product["id"], product["name"], quantity, product["price"] * quantity))
        row = db.execute("SELECT * FROM requests WHERE owner = ? AND request_key = ?", (alias, key)).fetchone()
        if row["product_id"] != product["id"] or row["quantity"] != quantity:
            raise RequestError(409)
        return (201 if result.rowcount else 200), request_view(row)

    def confirm_request(self, db, request_id):
        db.execute("UPDATE requests SET status = 'CONFIRMED' WHERE id = ?", (request_id,))
        row = db.execute("SELECT * FROM requests WHERE id = ?", (request_id,)).fetchone()
        if row is None:
            raise RequestError(404)
        return 200, request_view(row)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Servidor de demostración de Lou Araya.")
    parser.add_argument("--host", default="127.0.0.1")
    args = parser.parse_args()
    initialize()
    with ThreadingHTTPServer((args.host, 8765), Handler) as server:
        print(f"Lou Araya · {args.host}:8765", flush=True)
        server.serve_forever()
