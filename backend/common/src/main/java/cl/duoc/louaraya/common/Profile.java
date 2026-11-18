package cl.duoc.louaraya.common;

public record Profile(
        long id,
        String name,
        String alias,
        Role role,
        int avatar,
        String contact,
        boolean notificationsEnabled,
        String preferredCategory
) {
    public enum Role { CLIENT, ADMIN }

    public void requireAdmin() {
        if (role != Role.ADMIN) {
            throw ApiFailure.forbidden();
        }
    }

    public void requireClient() {
        if (role != Role.CLIENT) {
            throw ApiFailure.forbidden();
        }
    }
}
