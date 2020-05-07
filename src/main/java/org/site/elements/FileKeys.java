package org.site.elements;

import org.apache.commons.fileupload.FileItemStream;
import org.site.view.VUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class FileKeys {
    String hostDir;
    int dir = 1;
    int file = 1;
    String ext = "";
    long tempKey = VUtil.nowGMT().toEpochSecond() * 10000 + ((int) Math.round(Math.random() * 10000));
    public ArrayList<String> postFiles = new ArrayList<>();

    public FileKeys(String host) {
        hostDir = VUtil.pathToImageFolder(host);
        try {
            for (Path path : Files.list(Paths.get(hostDir)).filter(Files::isDirectory).collect(Collectors.toList())) {
                dir = Math.max(VUtil.getIntSelect(path.getFileName().toString()), dir);
            }
            for (Path path : Files.list(Paths.get(imageDir())).filter(Files::isRegularFile).collect(Collectors.toList())) {
                file = Math.max(VUtil.getIntSelect(path.getFileName().toString().substring(0, 4)), file);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Path postAttach(FileItemStream item) {
        Path path = Paths.get(nextImage(item.getName()));
        postFiles.add(fileKey());
        return path;
    }

    public Path postTempFile(FileItemStream item) {
        String pathTemp = VUtil.DIRTemp + (++tempKey) + "." + VUtil.getFileExt(item.getName());
        postFiles.add(pathTemp);
        return Paths.get(pathTemp);
    }

    String dirName() {
        String val = "0000" + dir;
        return val.substring(val.length() - 4);
    }

    String fileName() {
        String val = "0000" + file;
        return val.substring(val.length() - 4);
    }

    String imageDir() {
        return hostDir + dirName() + "/";
    }

    public String nextImage(String sourceFileName) {
        ext = VUtil.getFileExt(sourceFileName).replace("jpeg", "jpg");
        file++;
        if (file > 9999) {
            file = 1;
            dir++;
        }
        return fullName();
    }

    public String fileKey() {
        return dirName() + "/" + fileName() + "." + ext;
    }

    String fullName() {
        return hostDir + "/" + fileKey();
    }

}
