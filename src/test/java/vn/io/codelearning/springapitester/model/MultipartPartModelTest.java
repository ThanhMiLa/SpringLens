package vn.io.codelearning.springapitester.model;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class MultipartPartModelTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testValidFileValidatesSuccessfully() throws IOException {
        File file = tempFolder.newFile("test.txt");
        MultipartPartModel part = new MultipartPartModel("file", file, "text/plain");
        part.validate();
        Assert.assertEquals("test.txt", part.getFilenameOverride());
        Assert.assertFalse(part.isLargeFile());
    }

    @Test
    public void testNonExistentFileThrowsException() {
        File file = new File(tempFolder.getRoot(), "does-not-exist.bin");
        MultipartPartModel part = new MultipartPartModel("doc", file);
        IllegalArgumentException ex = Assert.assertThrows(IllegalArgumentException.class, part::validate);
        Assert.assertTrue(ex.getMessage().contains("File not found"));
    }

    @Test
    public void testDirectoryThrowsException() throws IOException {
        File dir = tempFolder.newFolder("subfolder");
        MultipartPartModel part = new MultipartPartModel("folderPart", dir);
        IllegalArgumentException ex = Assert.assertThrows(IllegalArgumentException.class, part::validate);
        Assert.assertTrue(ex.getMessage().contains("Path is a directory"));
    }

    @Test
    public void testUnreadableFileThrowsException() throws IOException {
        File file = tempFolder.newFile("unreadable.txt");
        if (file.setReadable(false)) {
            try {
                MultipartPartModel part = new MultipartPartModel("secret", file);
                IllegalArgumentException ex = Assert.assertThrows(IllegalArgumentException.class, part::validate);
                Assert.assertTrue(ex.getMessage().contains("cannot be read"));
            } finally {
                file.setReadable(true);
            }
        }
    }

    @Test
    public void testTextPartValidationDoesNotThrow() {
        MultipartPartModel textPart = new MultipartPartModel("username", "john_doe", "text/plain");
        textPart.validate();
        Assert.assertFalse(textPart.isFile());
        Assert.assertEquals("john_doe", textPart.getTextValue());
    }

    @Test
    public void testLargeFileDetectionAndCap() throws IOException {
        File largeFile = tempFolder.newFile("large.dat");
        // Create sparse file with length = 55 MB
        try (RandomAccessFile raf = new RandomAccessFile(largeFile, "rw")) {
            raf.setLength(55L * 1024 * 1024);
        }
        MultipartPartModel part = new MultipartPartModel("upload", largeFile);
        Assert.assertTrue(part.isLargeFile());
        // 55 MB is <= 100 MB max limit, so validate passes
        part.validate();

        // Now set length = 105 MB (exceeding 100 MB max limit)
        try (RandomAccessFile raf = new RandomAccessFile(largeFile, "rw")) {
            raf.setLength(105L * 1024 * 1024);
        }
        IllegalArgumentException ex = Assert.assertThrows(IllegalArgumentException.class, part::validate);
        Assert.assertTrue(ex.getMessage().contains("exceeds maximum allowed upload size"));
    }
}
