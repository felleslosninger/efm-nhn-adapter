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
import no.difi.meldingsutveksling.nhn.adapter.config.LimitsConfig
import no.difi.meldingsutveksling.nhn.adapter.handlers.InHandler
import no.difi.meldingsutveksling.nhn.adapter.handlers.LookupHandler
import no.difi.meldingsutveksling.nhn.adapter.handlers.MshConfigurationHandler
import no.difi.meldingsutveksling.nhn.adapter.handlers.NotificationHandler
import no.difi.meldingsutveksling.nhn.adapter.handlers.OutHandler
import no.difi.meldingsutveksling.nhn.adapter.model.ArDetails
import no.difi.meldingsutveksling.nhn.adapter.model.ContentTypes
import no.difi.meldingsutveksling.nhn.adapter.model.DeleteNotificationsInput
import no.difi.meldingsutveksling.nhn.adapter.model.GetDocumentInput
import no.difi.meldingsutveksling.nhn.adapter.model.IncomingMessage
import no.difi.meldingsutveksling.nhn.adapter.model.MessageStatus
import no.difi.meldingsutveksling.nhn.adapter.model.MshConfigurationDetails
import no.difi.meldingsutveksling.nhn.adapter.model.MshConfigurationInput
import no.difi.meldingsutveksling.nhn.adapter.model.MultipartNames
import no.difi.meldingsutveksling.nhn.adapter.model.Notification
import no.difi.meldingsutveksling.nhn.adapter.security.SecurityBeans.ADMIN_SCOPE
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
            path = ApiPaths.UNREAD_NOTIFICATIONS,
            method = [RequestMethod.GET],
            beanClass = NotificationHandler::class,
            beanMethod = "getUnreadNotifications",
            operation =
                Operation(
                    operationId = "getUnreadNotifications",
                    summary = "Get the unread notifications from MSH for a HER-id",
                    description =
                        "A NEW_MESSAGE notification announces an incoming message. A notification stays unread " +
                            "until it is deleted.",
                    parameters =
                        [
                            Parameter(
                                name = "herId",
                                `in` = ParameterIn.QUERY,
                                required = true,
                                description = "The HER-id to get notifications for",
                                example = "8144796",
                            ),
                            Parameter(
                                name = "notificationsToFetch",
                                `in` = ParameterIn.QUERY,
                                description =
                                    "How many notifications to get (1-1000). Defaults to " +
                                        "nhn.services.msh.notifications-to-fetch.",
                                example = "100",
                            ),
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
                                            array = ArraySchema(schema = Schema(implementation = Notification::class)),
                                            examples = [ExampleObject(value = ApiExamples.UNREAD_NOTIFICATIONS)],
                                        )
                                    ],
                            )
                        ],
                ),
        ),
        RouterOperation(
            path = ApiPaths.DELETE_NOTIFICATIONS,
            method = [RequestMethod.POST],
            beanClass = NotificationHandler::class,
            beanMethod = "deleteNotifications",
            operation =
                Operation(
                    operationId = "deleteNotifications",
                    summary = "Delete notifications in MSH, at most 1000 at a time",
                    requestBody =
                        RequestBody(
                            content =
                                [
                                    Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = Schema(implementation = DeleteNotificationsInput::class),
                                        examples = [ExampleObject(value = ApiExamples.DELETE_NOTIFICATIONS_INPUT)],
                                    )
                                ]
                        ),
                    responses = [ApiResponse(responseCode = "200", description = SUCCESS)],
                ),
        ),
        RouterOperation(
            path = ApiPaths.INCOMING_MESSAGE,
            method = [RequestMethod.GET],
            beanClass = InHandler::class,
            beanMethod = "getMessage",
            operation =
                Operation(
                    operationId = "getMessage",
                    summary = "Get the metadata of an incoming message",
                    parameters =
                        [
                            Parameter(
                                name = "messageId",
                                `in` = ParameterIn.PATH,
                                description = "The message ID, from a NEW_MESSAGE notification",
                                example = ApiExamples.MESSAGE_ID,
                            ),
                            Parameter(
                                name = "receiverHerId",
                                `in` = ParameterIn.QUERY,
                                required = true,
                                description = "The receiver the message is got for",
                                example = "8144796",
                            ),
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
                                            schema = Schema(implementation = IncomingMessage::class),
                                            examples = [ExampleObject(value = ApiExamples.INCOMING_MESSAGE)],
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
            path = ApiPaths.INCOMING_MESSAGE_DOWNLOADED,
            method = [RequestMethod.POST],
            beanClass = InHandler::class,
            beanMethod = "markMessageDownloaded",
            operation =
                Operation(
                    operationId = "markMessageDownloaded",
                    summary = "Mark a message as downloaded, which deletes its business document from MSH",
                    description = "The NEW_MESSAGE notification of the message is not deleted.",
                    parameters =
                        [
                            Parameter(
                                name = "messageId",
                                `in` = ParameterIn.PATH,
                                description = "The message ID",
                                example = ApiExamples.MESSAGE_ID,
                            ),
                            Parameter(
                                name = "receiverHerId",
                                `in` = ParameterIn.QUERY,
                                required = true,
                                description = "The receiver that downloaded the message",
                                example = "8144796",
                            ),
                        ],
                    responses = [ApiResponse(responseCode = "200", description = SUCCESS)],
                ),
        ),
        RouterOperation(
            path = ApiPaths.MSH_CONFIGURATIONS,
            method = [RequestMethod.GET],
            beanClass = MshConfigurationHandler::class,
            beanMethod = "list",
            operation =
                Operation(
                    operationId = "listMshConfigurations",
                    summary = "List the MSH configurations of the HER-ids",
                    description =
                        "Requires the scope $ADMIN_SCOPE. lastUsed is the last time a message was sent or received.",
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
                                                ArraySchema(
                                                    schema = Schema(implementation = MshConfigurationDetails::class)
                                                ),
                                        )
                                    ],
                            )
                        ],
                ),
        ),
        RouterOperation(
            path = ApiPaths.MSH_CONFIGURATION,
            method = [RequestMethod.PUT],
            beanClass = MshConfigurationHandler::class,
            beanMethod = "register",
            operation =
                Operation(
                    operationId = "registerMshConfiguration",
                    summary = "Register the MSH configuration of a HER-id in MSH, or replace it",
                    description =
                        "Requires the scope $ADMIN_SCOPE. A HER-id is also registered the first time a client uses " +
                            "it. 404 if the HER-id is not in Adresseregisteret.",
                    parameters = [Parameter(name = "herId", `in` = ParameterIn.PATH, example = "8144796")],
                    requestBody =
                        RequestBody(
                            required = false,
                            content =
                                [
                                    Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = Schema(implementation = MshConfigurationInput::class),
                                    )
                                ],
                        ),
                    responses =
                        [
                            ApiResponse(
                                responseCode = "200",
                                description = SUCCESS,
                                content =
                                    [
                                        Content(
                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                            schema = Schema(implementation = MshConfigurationDetails::class),
                                        )
                                    ],
                            )
                        ],
                ),
        ),
        RouterOperation(
            path = ApiPaths.MSH_CONFIGURATION,
            method = [RequestMethod.DELETE],
            beanClass = MshConfigurationHandler::class,
            beanMethod = "delete",
            operation =
                Operation(
                    operationId = "deleteMshConfiguration",
                    summary = "Delete the MSH configuration of a HER-id from MSH",
                    description =
                        "Requires the scope $ADMIN_SCOPE. 404 if the adapter has no configuration of the HER-id. " +
                            "A HER-id still in use is registered again the next time a client uses it.",
                    parameters = [Parameter(name = "herId", `in` = ParameterIn.PATH, example = "8144796")],
                    responses = [ApiResponse(responseCode = "200", description = SUCCESS)],
                ),
        ),
    )
    fun router(
        inHandler: InHandler,
        outHandler: OutHandler,
        lookupHandler: LookupHandler,
        notificationHandler: NotificationHandler,
        mshConfigurationHandler: MshConfigurationHandler,
        limits: LimitsConfig,
    ): RouterFunction<ServerResponse> =
        coRouter {
                inHandler(inHandler)
                outHandler(outHandler)
                lookupHandler(lookupHandler)
                notificationHandler(notificationHandler)
                mshConfigurationHandler(mshConfigurationHandler)
            }
            .filter(requestSizeFilter(limits.maxRequestSize))
            // Added last, so that it also handles the errors from the filters above
            .filter(nhnErrorFilter())
}
