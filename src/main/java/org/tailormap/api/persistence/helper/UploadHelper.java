package org.tailormap.api.persistence.helper;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.tailormap.api.util.Constants.UUID_REGEX;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.tailormap.api.controller.UploadsController;
import org.tailormap.api.repository.UploadRepository;

@Service
public class UploadHelper {

  private final UploadRepository uploadRepository;

  public UploadHelper(UploadRepository uploadRepository) {
    this.uploadRepository = uploadRepository;
  }

  public String getUrlForImage(String imageId, String category) {
    if (imageId == null || !imageId.matches(UUID_REGEX)) {
      return null;
    }
    return uploadRepository
        .findByIdAndCategory(UUID.fromString(imageId), category)
        .map(
            upload ->
                linkTo(
                        UploadsController.class,
                        Map.of(
                            "id", imageId, "category", category, "filename", upload.getFilename()))
                    .toString())
        .orElse(null);
  }
}
