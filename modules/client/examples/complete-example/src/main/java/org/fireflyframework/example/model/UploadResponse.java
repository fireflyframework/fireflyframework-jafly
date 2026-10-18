package org.fireflyframework.example.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {
    private String fileId;
    private String fileName;
    private Long fileSize;
    private String url;
    private String status;
}

