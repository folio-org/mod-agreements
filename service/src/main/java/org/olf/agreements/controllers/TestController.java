package org.olf.agreements.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.responses.*;


import java.util.ArrayList;
import java.util.List;

import org.springframework.http.MediaType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;


import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonView;

import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import java.util.Map;


// See https://github.com/bezkoder/spring-boot-3-rest-api-example

@Tag(name = "Tutorial", description = "Tutorial management APIs")
@RestController
public class TestController {

  @Data
  @AllArgsConstructor
  @Builder
  @ToString
  public static class DummyResponse {
    String code;
  }

  @Operation(
      summary = "Retrieve a Tutorial by Id",
      description = "Get a Tutorial object by specifying its id. The response is Tutorial object with id, title, description and published status.",
      tags = { "one", "two" } )
  @ApiResponses({
    @ApiResponse(responseCode = "200", content = { @Content(schema = @Schema(implementation = DummyResponse.class), mediaType = "application/json") }),
    @ApiResponse(responseCode = "404", content = { @Content(schema = @Schema()) }),
    @ApiResponse(responseCode = "500", content = { @Content(schema = @Schema()) }) })
  @GetMapping(value="/tutorials/{id}")
  public @ResponseBody ResponseEntity<DummyResponse> getTutorialById(@PathVariable("id") long id) {
    DummyResponse dr = new DummyResponse("Wibble");
    return new ResponseEntity(dr, HttpStatus.OK);
  }

}
