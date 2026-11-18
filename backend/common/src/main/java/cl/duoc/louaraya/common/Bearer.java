package cl.duoc.louaraya.common;

public final class Bearer {
    private Bearer() {}

    public static String require(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw ApiFailure.unauthorized();
        }
        String token = authorization.substring(7);
        if (!token.matches("[A-Za-z0-9_-]{43}")) {
            throw ApiFailure.unauthorized();
        }
        return token;
    }
}
