/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.controller.admin;

import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.tailormap.api.repository.UploadMatch;
import org.tailormap.api.repository.UploadRepository;

@RestController
public class FindUploadsByHashController {
  private final UploadRepository uploadRepository;

  public FindUploadsByHashController(UploadRepository uploadRepository) {
    this.uploadRepository = uploadRepository;
  }

  @PostMapping(
      path = "${tailormap-api.admin.base-path}/uploads/find-by-hash/{category}",
      consumes = "application/json")
  public List<UploadMatch> findUploadsByHash(@PathVariable String category, @RequestBody List<String> hashes) {
    return uploadRepository.findByContentMd5In(category, hashes);
  }
}
