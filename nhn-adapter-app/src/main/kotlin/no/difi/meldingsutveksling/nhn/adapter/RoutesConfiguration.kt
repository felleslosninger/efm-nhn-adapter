package no.difi.meldingsutveksling.nhn.adapter

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.ArraySchema
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
import no.difi.meldingsutveksling.nhn.adapter.model.IncomingMessage
import no.difi.meldingsutveksling.nhn.adapter.model.MessageStatus
import no.difi.meldingsutveksling.nhn.adapter.model.MultipartNames
import org.springdoc.core.annotations.RouterOperation
import org.springdoc.core.annotations.RouterOperations
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.coRouter

private const val SUCCESS = "Success"
private const val FORRETNINGSMELDING_DESCRIPTION = "JWE containing a SBD"
private const val DOKUMENTPAKKE_DESCRIPTION = "CMS encrypted ASiC-e"

/** Wires the API routes to their handlers. The annotations document the routes for springdoc. */
@Configuration
class RoutesConfiguration {

    @Bean(name = ["routerFunction"])
    @RouterOperations(
        RouterOperation(
            path = ApiPaths.LOOKUP,
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
                                example = ApiExamples.HER_ID_IDENTIFIER,
                            )
                        ],
                    responses =
                        [
                            ApiResponse(
                                responseCode = "200",
                                description = SUCCESS,
                                content =
                                    [
                                        Content(
                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                            schema = Schema(implementation = ArDetails::class),
                                            examples = [ExampleObject(value = ApiExamples.AR_DETAILS)],
                                        )
                                    ],
                            )
                        ],
                ),
        ),
        RouterOperation(
            path = ApiPaths.OUTGOING_MESSAGE_STATUSES,
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
                                example = ApiExamples.MESSAGE_ID,
                            )
                        ],
                    responses =
                        [
                            ApiResponse(
                                responseCode = "200",
                                description = SUCCESS,
                                content =
                                    [
                                        Content(
                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                            array =
                                                ArraySchema(schema = Schema(implementation = MessageStatus::class)),
                                            examples = [ExampleObject(value = ApiExamples.MESSAGE_STATUSES)],
                                        )
                                    ],
                            )
                        ],
                ),
        ),
        RouterOperation(
            path = ApiPaths.OUTGOING_MESSAGES,
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
                                [
                                    Content(
                                        mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                                        schema = Schema(type = "object"),
                                        schemaProperties =
                                            [
                                                SchemaProperty(
                                                    name = MultipartNames.FORRETNINGSMELDING,
                                                    schema =
                                                        Schema(
                                                            type = "string",
                                                            format = "base64",
                                                            contentMediaType = ContentTypes.APPLICATION_JOSE,
                                                            description = FORRETNINGSMELDING_DESCRIPTION,
                                                        ),
                                                ),
                                                SchemaProperty(
                                                    name = MultipartNames.DOKUMENTPAKKE,
                                                    schema =
                                                        Schema(
                                                            type = "object",
                                                            format = "binary",
                                                            contentMediaType = ContentTypes.APPLICATION_ASICE,
                                                            description = DOKUMENTPAKKE_DESCRIPTION,
                                                        ),
                                                ),
                                            ],
                                    )
                                ]
                        ),
                    responses =
                        [
                            ApiResponse(
                                responseCode = "200",
                                description = SUCCESS,
                                content =
                                    [
                                        Content(
                                            mediaType = MediaType.TEXT_PLAIN_VALUE,
                                            examples =
                                                [
                                                    ExampleObject(
                                                        description = "External message reference",
                                                        value = ApiExamples.MESSAGE_REFERENCE,
                                                    )
                                                ],
                                        )
                                    ],
                            )
                        ],
                ),
        ),
        RouterOperation(
            path = ApiPaths.INCOMING_MESSAGES,
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
                                description = SUCCESS,
                                content =
                                    [
                                        Content(
                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                            array =
                                                ArraySchema(schema = Schema(implementation = IncomingMessage::class)),
                                            examples = [ExampleObject(value = ApiExamples.INCOMING_MESSAGES)],
                                        )
                                    ],
                            )
                        ],
                ),
        ),
        RouterOperation(
            path = ApiPaths.INCOMING_MESSAGES,
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
                                [
                                    Content(
                                        mediaType = ContentTypes.APPLICATION_JOSE,
                                        schema =
                                            Schema(
                                                type = "object",
                                                format = "JWE",
                                                description = ApiExamples.GET_DOCUMENT_INPUT,
                                                implementation = GetDocumentInput::class,
                                            ),
                                    )
                                ]
                        ),
                    responses =
                        [
                            ApiResponse(
                                responseCode = "200",
                                description = SUCCESS,
                                content =
                                    [
                                        Content(
                                            mediaType = MediaType.MULTIPART_MIXED_VALUE,
                                            schema = Schema(type = "object"),
                                            schemaProperties =
                                                [
                                                    SchemaProperty(
                                                        name = MultipartNames.FORRETNINGSMELDING,
                                                        schema =
                                                            Schema(
                                                                type = "string",
                                                                format = "base64",
                                                                contentMediaType = ContentTypes.APPLICATION_JOSE,
                                                                description = FORRETNINGSMELDING_DESCRIPTION,
                                                            ),
                                                    ),
                                                    SchemaProperty(
                                                        name = MultipartNames.DOKUMENTPAKKE,
                                                        schema =
                                                            Schema(
                                                                type = "object",
                                                                format = "binary",
                                                                contentMediaType = ContentTypes.APPLICATION_ASICE,
                                                                description = DOKUMENTPAKKE_DESCRIPTION,
                                                            ),
                                                    ),
                                                ],
                                        )
                                    ],
                            )
                        ],
                ),
        ),
        RouterOperation(
            path = ApiPaths.INCOMING_MESSAGE_READ,
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
                                example = ApiExamples.MESSAGE_ID,
                            )
                        ],
                    responses = [ApiResponse(responseCode = "200", description = SUCCESS)],
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
