package info.kgeorgiy.ja.ulin.walk;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashOfFile {
    private final static int BUFFER_SIZE = 16384;
    private final int hashLength;
    private final String zeroHash;
    private MessageDigest hashSum;

    public HashOfFile(int hashLength, String hashAlgorithm) {
        this.hashLength = hashLength;
        this.zeroHash = "0".repeat(hashLength * 2);

        try {
            hashSum = MessageDigest.getInstance(hashAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            System.err.println("No such algorithm: " + e.getMessage());
        }
    }

    private String formatHash(byte[] hashBytes) {
        StringBuilder hexString = new StringBuilder();
        for (int i = 0; i < hashLength; i++) {
            hexString.append(String.format("%02x", hashBytes[i]));
        }
        return hexString.toString();
    }

    public String getZeroHash() {
        return zeroHash;
    }

    public String getHashOfFile(Path filePath) {
        hashSum.reset();

        try (InputStream fileInputStream = Files.newInputStream(filePath.toAbsolutePath());
             BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = bufferedInputStream.read(buffer)) != -1) {
                hashSum.update(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            return zeroHash;
        }

        return formatHash(hashSum.digest());
    }
}
