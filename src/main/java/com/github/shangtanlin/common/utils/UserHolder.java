package com.github.shangtanlin.common.utils;

import com.github.shangtanlin.model.dto.user.UserDTO;

public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static UserDTO getUser() {
        return tl.get();
    }

    public static void setUser(UserDTO userDTO) {
        tl.set(userDTO);
    }

    public static void removeUser() {
        tl.remove();
    }
}
