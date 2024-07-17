package com.rte_france.antares.datamanager_back.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Set;

@Value
@Builder(toBuilder = true)
@JsonDeserialize(builder = AttachmentOutputDto.AttachmentOutputDtoBuilder.class)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AttachmentOutputDto {

    @JsonProperty("id")
    String id;

    @JsonProperty("name")
    String name;

    @JsonProperty("type")
    String type;

    @JsonProperty("size")
    long size;

    @JsonProperty("content_type")
    String contentType;

    @JsonProperty("created_by")
    String createdBy;

    @JsonProperty("creation_date")
    Instant creationDate;

}
