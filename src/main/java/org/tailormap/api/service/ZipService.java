/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.service;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Service for zipping directories and other useful operations concerning zip files. */
@Service
public class ZipService {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  /**
   * Zips the contents of a directory into a zip file.
   *
   * @param sourceDir the directory to zip
   * @param zipFile the resulting zip file
   * @throws IOException if an I/O error occurs
   */
  public void zipDirectory(Path sourceDir, Path zipFile) throws IOException {
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile));
        Stream<Path> pathStream = Files.walk(sourceDir)) {
      pathStream.filter(Files::isRegularFile).forEach(path -> {
        String entryName = sourceDir.relativize(path).toString().replace(File.separatorChar, '/');
        try {
          logger.trace("Adding file {} to zip {}", path, zipFile);
          zos.putNextEntry(new ZipEntry(entryName));
          Files.copy(path, zos);
          zos.closeEntry();
        } catch (IOException e) {
          throw new RuntimeException("Failed to add file to zip: " + path, e);
        }
      });
    } catch (RuntimeException e) {
      if (e.getCause() instanceof IOException ioException) {
        throw ioException;
      }
      throw e;
    }
  }
}
