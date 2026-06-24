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
    private Storage storage = new Storage();

    public Ssh getSsh() { return ssh; }
    public void setSsh(Ssh ssh) { this.ssh = ssh; }

    public Ftps getFtps() { return ftps; }
    public void setFtps(Ftps ftps) { this.ftps = ftps; }

    public File getFile() { return file; }
    public void setFile(File file) { this.file = file; }

    public Http getHttp() { return http; }
    public void setHttp(Http http) { this.http = http; }

    public Storage getStorage() { return storage; }
    public void setStorage(Storage storage) { this.storage = storage; }

    public static class Storage {
        private String type = "local";
        private S3 s3 = new S3();
        private Obs obs = new Obs();

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public S3 getS3() { return s3; }
        public void setS3(S3 s3) { this.s3 = s3; }

        public Obs getObs() { return obs; }
        public void setObs(Obs obs) { this.obs = obs; }

        public static class S3 {
            private String bucket = "";
            private String region = "us-east-1";
            private String accessKey = "";
            private String secretKey = "";

            public String getBucket() { return bucket; }
            public void setBucket(String bucket) { this.bucket = bucket; }
            public String getRegion() { return region; }
            public void setRegion(String region) { this.region = region; }
            public String getAccessKey() { return accessKey; }
            public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
            public String getSecretKey() { return secretKey; }
            public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        }

        public static class Obs {
            private String bucket = "";
            private String endpoint = "";
            private String accessKey = "";
            private String secretKey = "";

            public String getBucket() { return bucket; }
            public void setBucket(String bucket) { this.bucket = bucket; }
            public String getEndpoint() { return endpoint; }
            public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
            public String getAccessKey() { return accessKey; }
            public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
            public String getSecretKey() { return secretKey; }
            public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        }
    }

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
