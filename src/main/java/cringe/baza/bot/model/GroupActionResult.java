package cringe.baza.bot.model;

import cringe.baza.domain.MemeGroup;

public record GroupActionResult(Status status, MemeGroup group) {

    public enum Status {
        OK,
        NOT_FOUND
    }

    public static GroupActionResult ok(MemeGroup group) {
        return new GroupActionResult(Status.OK, group);
    }

    public static GroupActionResult notFound() {
        return new GroupActionResult(Status.NOT_FOUND, null);
    }
}
