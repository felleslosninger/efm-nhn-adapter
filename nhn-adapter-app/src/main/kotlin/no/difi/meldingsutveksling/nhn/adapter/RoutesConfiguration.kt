package no.difi.meldingsutveksling.nhn.adapter

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.SchemaProperty
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import no.difi.meldingsutveksling.nhn.adapter.handlers.InHandler
import no.difi.meldingsutveksling.nhn.adapter.handlers.LookupHandler
import no.difi.meldingsutveksling.nhn.adapter.handlers.OutHandler
import no.difi.meldingsutveksling.nhn.adapter.model.ArDetails
import no.difi.meldingsutveksling.nhn.adapter.model.ContentTypes
import no.difi.meldingsutveksling.nhn.adapter.model.GetDocumentInput
import no.difi.meldingsutveksling.nhn.adapter.model.MessageStatus
import no.difi.meldingsutveksling.nhn.adapter.model.MultipartNames
import no.ks.fiks.nhn.msh.MessageWithMetadata
import org.springdoc.core.annotations.RouterOperation
import org.springdoc.core.annotations.RouterOperations
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class RoutesConfiguration {

    @Bean(name = ["routerFunction"])
    @RouterOperations(
        RouterOperation(
            path = "/api/lookup/{identifier}",
            method = [RequestMethod.GET],
            beanClass = LookupHandler::class,
            beanMethod = "arLookup",
            operation =
                Operation(
                    operationId = "arLookup",
                    summary = "Lookup identifier in Adresseregisteret",
                    parameters =
                        [
                            Parameter(
                                name = "identifier",
                                `in` = ParameterIn.PATH,
                                description =
                                    "The identifier to look up. Can be her-id:<her-ID> or fastlege-for:<PID>",
                                example = "her-id:8144796",
                            )
                        ],
                    responses =
                        [
                            ApiResponse(
                                responseCode = "200",
                                description = "Success",
                                content =
                                    arrayOf(
                                        Content(
                                            mediaType = "application/json",
                                            schema = Schema(implementation = ArDetails::class),
                                            examples =
                                                [
                                                    ExampleObject(
                                                        value =
                                                            """{
  "parentHerId": 8139672,
  "parentName": "WebMed Partner - LIVSGLAD VIDSYNT IMPALA SAMEIE",
  "orgNumber": "313464091",
  "herId": 8144796,
  "name": "Peter Peterson",
  "derCertificate": "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"
}"""
                                                    )
                                                ],
                                        )
                                    ),
                            )
                        ],
                ),
        ),
        RouterOperation(
            path = "/api/messages/out/{messageId}/statuses",
            method = [RequestMethod.GET],
            beanClass = OutHandler::class,
            beanMethod = "getStatus",
            operation =
                Operation(
                    operationId = "getStatus",
                    summary = "Get status for an outgoing message",
                    parameters =
                        [
                            Parameter(
                                name = "messageId",
                                `in` = ParameterIn.PATH,
                                description = "The message ID",
                                example = "02b7e1cd-97af-47f4-880d-bd6ef6833171",
                            )
                        ],
                    responses =
                        [
                            ApiResponse(
                                responseCode = "200",
                                description = "Success",
                                content =
                                    arrayOf(
                                        Content(
                                            mediaType = "application/json",
                                            schema = Schema(implementation = MessageStatus::class),
                                            examples =
                                                [
                                                    ExampleObject(
                                                        value =
                                                            """[
  {
    "receiverHerId": 8144796,
    "transportStatus": "ACKNOWLEDGED",
    "apprecStatus": "OK"
  }
]}"""
                                                    )
                                                ],
                                        )
                                    ),
                            )
                        ],
                ),
        ),
        RouterOperation(
            path = "/api/messages/out",
            method = [RequestMethod.POST],
            beanClass = OutHandler::class,
            beanMethod = "sendMessage",
            operation =
                Operation(
                    operationId = "sendMessage",
                    summary = "Send a message",
                    requestBody =
                        RequestBody(
                            content =
                                arrayOf(
                                    Content(
                                        mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                                        schema = Schema(type = "object"),
                                        schemaProperties =
                                            arrayOf(
                                                SchemaProperty(
                                                    name = MultipartNames.FORRETNINGSMELDING,
                                                    schema =
                                                        Schema(
                                                            type = "string",
                                                            format = "base64",
                                                            contentMediaType = ContentTypes.APPLICATION_JOSE,
                                                            description = "JWE containing a SBD",
                                                        ),
                                                ),
                                                SchemaProperty(
                                                    name = MultipartNames.DOKUMENTPAKKE,
                                                    schema =
                                                        Schema(
                                                            type = "object",
                                                            format = "binary",
                                                            contentMediaType = ContentTypes.APPLICATION_ASICE,
                                                            description = "CMS encrypted ASiC-e",
                                                        ),
                                                ),
                                            ),
                                    )
                                )
                        ),
                    responses =
                        [
                            ApiResponse(
                                responseCode = "200",
                                description = "Success",
                                content =
                                    arrayOf(
                                        Content(
                                            mediaType = MediaType.TEXT_PLAIN_VALUE,
                                            examples =
                                                [
                                                    ExampleObject(
                                                        description = "External message reference",
                                                        value = "c684a535-ab0f-4a8b-b9de-8ed8623f673d",
                                                    )
                                                ],
                                        )
                                    ),
                            )
                        ],
                ),
        ),
        RouterOperation(
            path = "/api/messages/in",
            method = [RequestMethod.GET],
            beanClass = InHandler::class,
            beanMethod = "getMessagesWithMetadata",
            operation =
                Operation(
                    operationId = "getMessagesWithMetadata",
                    summary = "Get incoming messages with metadata",
                    responses =
                        [
                            ApiResponse(
                                responseCode = "200",
                                description = "Success",
                                content =
                                    arrayOf(
                                        Content(
                                            mediaType = "application/json",
                                            schema = Schema(implementation = MessageWithMetadata::class),
                                            examples =
                                                [
                                                    ExampleObject(
                                                        value =
                                                            """{
  "id": "3ef83023-3532-4f9c-9b9a-0ebfba6de4c5",
  "contentType": "application/xml",
  "receiverHerId": 8144796
  "senderHerId": 8144717
  "businessDocumentId": "1c62e13e-e05b-431b-81f9-197cb794fabc"
  "businessDocumentDate": "2026-06-26T14:30:45+02:00"
  "isAppRec": false
}"""
                                                    )
                                                ],
                                        )
                                    ),
                            )
                        ],
                ),
        ),
        RouterOperation(
            path = "/api/messages/in",
            method = [RequestMethod.POST],
            beanClass = InHandler::class,
            beanMethod = "getBusinessDocument",
            operation =
                Operation(
                    operationId = "getBusinessDocument",
                    summary = "Get a business document",
                    requestBody =
                        RequestBody(
                            content =
                                arrayOf(
                                    Content(
                                        mediaType = "application/jose",
                                        schema =
                                            Schema(
                                                type = "object",
                                                format = "JWE",
                                                description =
                                                    "JWE with a JSON like this {\"id\":\"8baf0255-2687-49e3-9398-bbdf32bfccfc\" }.",
                                                implementation = GetDocumentInput::class,
                                            ),
                                    )
                                )
                        ),
                    responses =
                        [
                            ApiResponse(
                                responseCode = "200",
                                description = "Success",
                                content =
                                    arrayOf(
                                        Content(
                                            mediaType = MediaType.MULTIPART_MIXED_VALUE,
                                            schema = Schema(type = "object"),
                                            schemaProperties =
                                                arrayOf(
                                                    SchemaProperty(
                                                        name = MultipartNames.FORRETNINGSMELDING,
                                                        schema =
                                                            Schema(
                                                                type = "string",
                                                                format = "base64",
                                                                contentMediaType = ContentTypes.APPLICATION_JOSE,
                                                                description = "JWE containing a SBD",
                                                            ),
                                                    ),
                                                    SchemaProperty(
                                                        name = MultipartNames.DOKUMENTPAKKE,
                                                        schema =
                                                            Schema(
                                                                type = "object",
                                                                format = "binary",
                                                                contentMediaType = ContentTypes.APPLICATION_ASICE,
                                                                description = "CMS encrypted ASiC-e",
                                                            ),
                                                    ),
                                                ),
                                        )
                                    ),
                            )
                        ],
                ),
        ),
        RouterOperation(
            path = "/api/messages/in/{messageId}/read",
            method = [RequestMethod.POST],
            beanClass = InHandler::class,
            beanMethod = "markMessageRead",
            operation =
                Operation(
                    operationId = "markMessageRead",
                    summary = "Mark a message as read",
                    parameters =
                        [
                            Parameter(
                                name = "messageId",
                                `in` = ParameterIn.PATH,
                                description = "The message ID",
                                example = "02b7e1cd-97af-47f4-880d-bd6ef6833171",
                            )
                        ],
                    responses = [ApiResponse(responseCode = "200", description = "Success")],
                ),
        ),
    )
    fun router(
        inHandler: InHandler,
        outHandler: OutHandler,
        lookupHandler: LookupHandler,
    ): RouterFunction<ServerResponse> =
        coRouter {
                inHandler(inHandler)
                outHandler(outHandler)
                lookupHandler(lookupHandler)
            }
            .filter(nhnErrorFilter())
}
