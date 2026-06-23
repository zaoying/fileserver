package com.filesever.config;

import java.nio.file.Path;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "filesever")
public class FileServerProperties {

    private Ssh ssh = new Ssh();
    private Ftps ftps = new Ftps();
    private File file = new File();
    private Http http = new Http();

    public Ssh getSsh() { return ssh; }
    public void setSsh(Ssh ssh) { this.ssh = ssh; }

    public Ftps getFtps() { return ftps; }
    public void setFtps(Ftps ftps) { this.ftps = ftps; }

    public File getFile() { return file; }
    public void setFile(File file) { this.file = file; }

    public Http getHttp() { return http; }
    public void setHttp(Http http) { this.http = http; }

    public static class Ftps {
        private int port = 21;
        private boolean implicitSsl = false;
        private String keystorePassword = "filesever";
        private Path keystoreFile = Path.of("./ftps.jks");

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }

        public boolean isImplicitSsl() { return implicitSsl; }
        public void setImplicitSsl(boolean implicitSsl) { this.implicitSsl = implicitSsl; }

        public String getKeystorePassword() { return keystorePassword; }
        public void setKeystorePassword(String keystorePassword) { this.keystorePassword = keystorePassword; }

        public Path getKeystoreFile() { return keystoreFile; }
        public void setKeystoreFile(Path keystoreFile) { this.keystoreFile = keystoreFile; }
    }

    public static class Ssh {
        private int port = 2222;
        private Path hostKeyDir = Path.of("./hostkeys");
        private List<User> users = List.of(new User("admin", "admin"));
        private boolean disableHostKeyIpCheck = true;

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }

        public Path getHostKeyDir() { return hostKeyDir; }
        public void setHostKeyDir(Path hostKeyDir) { this.hostKeyDir = hostKeyDir; }

        public List<User> getUsers() { return users; }
        public void setUsers(List<User> users) { this.users = users; }

        public boolean isDisableHostKeyIpCheck() { return disableHostKeyIpCheck; }
        public void setDisableHostKeyIpCheck(boolean disableHostKeyIpCheck) { this.disableHostKeyIpCheck = disableHostKeyIpCheck; }
    }

    public static class File {
        private Path storageDir = Path.of("./files");

        public Path getStorageDir() { return storageDir; }
        public void setStorageDir(Path storageDir) { this.storageDir = storageDir; }
    }

    public static class Http {
        private int port = 8080;

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
    }

    public static class User {
        private String username;
        private String password;

        public User() {}

        public User(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
