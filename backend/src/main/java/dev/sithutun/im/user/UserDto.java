package dev.sithutun.im.user;

public record UserDto(String accountName, String displayName) {

    public static UserDto from(User user) {
        return new UserDto(user.getAccountName(), user.getDisplayName());
    }
}
