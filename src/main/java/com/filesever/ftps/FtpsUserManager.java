package com.filesever.ftps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ftpserver.ftplet.Authentication;
import org.apache.ftpserver.ftplet.AuthenticationFailedException;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.User;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;

import com.filesever.config.FileServerProperties;

public class FtpsUserManager implements UserManager {

    private final Map<String, User> users = new HashMap<>();

    public FtpsUserManager(List<FileServerProperties.User> userList) {
        for (var u : userList) {
            BaseUser user = new BaseUser();
            user.setName(u.getUsername());
            user.setPassword(u.getPassword());
            user.setHomeDirectory("/" + u.getUsername());
            user.setEnabled(true);
            List<Authority> authorities = new ArrayList<>();
            authorities.add(new WritePermission());
            user.setAuthorities(authorities);
            users.put(u.getUsername(), user);
        }
    }

    @Override
    public User authenticate(Authentication authentication)
            throws AuthenticationFailedException {
        try {
            var userMethod = authentication.getClass().getMethod("getUsername");
            var passMethod = authentication.getClass().getMethod("getPassword");
            String username = (String) userMethod.invoke(authentication);
            String password = (String) passMethod.invoke(authentication);
            User user = users.get(username);
            if (user != null && user.getPassword().equals(password)) {
                return user;
            }
        } catch (Exception e) {
        }
        throw new AuthenticationFailedException("Authentication failed");
    }

    @Override
    public User getUserByName(String name) { return users.get(name); }

    @Override
    public String[] getAllUserNames() {
        return users.keySet().toArray(new String[0]);
    }

    @Override
    public void save(User user) {
        users.put(user.getName(), user);
    }

    @Override
    public boolean doesExist(String name) {
        return users.containsKey(name);
    }

    @Override
    public void delete(String name) {
        users.remove(name);
    }

    @Override
    public boolean isAdmin(String name) {
        return false;
    }

    @Override
    public String getAdminName() {
        return null;
    }

}
