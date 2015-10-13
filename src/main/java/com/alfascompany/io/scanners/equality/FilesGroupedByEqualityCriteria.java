package com.alfascompany.io.scanners.equality;

import com.alfascompany.io.scanners.equality.criterias.SameNamePrefixEqualityCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

public class FilesGroupedByEqualityCriteria<T> implements Serializable {

    private static final long serialVersionUID = -795646043306946698L;

    private static final Logger logger = LoggerFactory.getLogger(FilesGroupedByEqualityCriteria.class);
    private final Map<T, ArrayList<ArrayList<ScannedFile>>> files = new HashMap<>(500);
    private final EqualityCriteria<T> equalityCriteria;

    public FilesGroupedByEqualityCriteria(final EqualityCriteria<T> equalityCriteria) {

        this.equalityCriteria = equalityCriteria;
    }

    public void addFile(final ScannedFile scannedFile) {

        final T fastPseudoUniqueKey = equalityCriteria.getFastPseudoUniqueKey(scannedFile);
        ArrayList<ArrayList<ScannedFile>> fileGroupList = files.get(fastPseudoUniqueKey);

        if (fileGroupList == null) {
            fileGroupList = new ArrayList<>();
            files.put(fastPseudoUniqueKey, fileGroupList);
        }

        // there can be more than one group of equals files that have the same pseudo key
        ArrayList<ScannedFile> selectedFileGroup = null;
        for (final ArrayList<ScannedFile> fileGroup : fileGroupList) {

            if (equalityCriteria.test(fileGroup.get(0), scannedFile)) {
                selectedFileGroup = fileGroup;
                break;
            }
        }

        if (selectedFileGroup == null) {
            selectedFileGroup = new ArrayList<>();
            fileGroupList.add(selectedFileGroup);
        }

        selectedFileGroup.add(scannedFile);
    }

    public void clearFiles() {
        files.clear();
    }

    public ArrayList<ArrayList<ScannedFile>> getEqualityFilesGroups(final boolean onlyWithRepeatedFiles) {

        final ArrayList<ArrayList<ScannedFile>> equalityFilesGroupList = new ArrayList<>(files.values().size());
        for (final ArrayList<ArrayList<ScannedFile>> equalityFilesGroups : files.values()) {

            for (final ArrayList<ScannedFile> scannedFiles : equalityFilesGroups) {
                if (!onlyWithRepeatedFiles || scannedFiles.size() > 1) {
                    equalityFilesGroupList.add(scannedFiles);
                }
            }
        }
        return equalityFilesGroupList;
    }

    public void printEqualityFilesGroupsSize() {

        long totalSize = 0;
        long duplicatedSize = 0;
        for (final ArrayList<ScannedFile> scannedFiles : getEqualityFilesGroups(false)) {

            duplicatedSize += scannedFiles.get(0).sizeInBytes;
            for (final ScannedFile scannedFile : scannedFiles) {
                totalSize += scannedFile.sizeInBytes;
            }
        }

        logger.info("Total size scanned {" + totalSize + "} bytes and duplicated files size {" + duplicatedSize + "} bytes");
        logger.info("Total size scanned {" + ((double) totalSize / 1024d / 1024d) + "} mb and duplicated files size {" + ((double) duplicatedSize / 1024d / 1024d) + "} mb");
    }

    public void printEqualityFilesGroups(final boolean onlyWithRepeatedFiles) {

        getEqualityFilesGroups(onlyWithRepeatedFiles).stream().forEach(e -> logger.info("Repeated files: " + e));
    }

    public void printEqualityFilesGroupsGroupByFolder(final boolean onlyWithRepeatedFiles) {

        getEqualityFilesGroups(onlyWithRepeatedFiles).stream().collect(Collectors.groupingBy(scannedFiles ->
                scannedFiles.stream().map(scannedFile -> scannedFile.folder).collect(Collectors.toList()))).keySet().forEach(e -> logger.info("Folder: " + e));
    }

    public void printEqualityFilesGroupsGroupThatMatchRule(final BiPredicate<ScannedFile, ScannedFile> rule) {

        for (ArrayList<ScannedFile> scannedFiles : getEqualityFilesGroups(false)) {

            boolean allEqual = true;
            for (ScannedFile scannedFile1 : scannedFiles) {
                for (ScannedFile scannedFile2 : new ArrayList<>(scannedFiles)) {

                    if (!rule.test(scannedFile1, scannedFile2)) {
                        allEqual = false;
                        break;
                    }
                }
                if (!allEqual) break;
            }
            if (!allEqual) {
                logger.debug("Not conforming to rule: " + scannedFiles);
            }
        }
    }

    public void removeDuplicated() {

        for (final ArrayList<ScannedFile> scannedFiles : getEqualityFilesGroups(true)) {

            final ScannedFile scannedFileWithLowerLengthName = scannedFiles.stream().min((f1, f2) -> Integer.compare(f1.name.length(), f2.name.length())).get();

            for (ScannedFile scannedFile : scannedFiles) {

                if (!scannedFileWithLowerLengthName.fullPath.equals(scannedFile.fullPath)) {
                    try {
                        Files.delete(Paths.get(scannedFile.fullPath));
                    } catch (final IOException e) {
                        logger.error("Error deleting file {" + scannedFile.fullPath + "} " + e.getMessage(), e);
                    }
                }

            }
        }
    }
}