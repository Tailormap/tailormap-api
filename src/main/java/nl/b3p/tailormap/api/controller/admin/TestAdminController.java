/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Serializable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestAdminController {
  @GetMapping(path = "${tailormap-api.admin.base-path}/test")
  public ResponseEntity<Serializable> test() {
    return ResponseEntity.ok(new ObjectMapper().createObjectNode().put("test", true));
  }
}
