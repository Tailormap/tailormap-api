/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.configuration;

import java.util.List;
import javax.annotation.PostConstruct;
import nl.b3p.tailormap.api.persistence.Catalog;
import nl.b3p.tailormap.api.persistence.json.CatalogNode;
import nl.b3p.tailormap.api.repository.CatalogRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

@Configuration
@Service("tailormap-database-initialization")
public class TailormapDatabaseInitialization {
  CatalogRepository catalogRepository;

  public TailormapDatabaseInitialization(CatalogRepository catalogRepository) {
    this.catalogRepository = catalogRepository;
  }

  @PostConstruct
  public void databaseInitialization() {
    Catalog catalog =
        new Catalog()
            .setId(Catalog.MAIN)
            .setNodes(List.of(new CatalogNode().root(true).title("root").id("root")));
    catalogRepository.save(catalog);
  }
}
