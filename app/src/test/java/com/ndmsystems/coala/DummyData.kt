package com.ndmsystems.coala

import java.io.File

/**
 * Created by Toukhvatullin Marat on 21.10.2019.
 */
enum class DummyData(private val fileName: String) {
    coapUrl("coapMessages/CoAPMessageDummyCoapUrl.bin"),
    coapsUrl("coapMessages/CoAPMessageDummyCoapsUrl.bin"),

    messageIdRegular("coapMessages/CoAPMessageDummyMessageIdRegular.bin"),
    messageIdMin("coapMessages/CoAPMessageDummyMessageIdMin.bin"),
    messageIdMax("coapMessages/CoAPMessageDummyMessageIdMax.bin"),

    absentToken("coapMessages/CoAPMessageDummyAbsentToken.bin"),
    existingToken("coapMessages/CoAPMessageDummyExistingToken.bin"),

    typeConfirmable("coapMessages/CoAPMessageDummyTypeConfirmable.bin"),
    typeNonConfirmable("coapMessages/CoAPMessageDummyTypeNonConfirmable.bin"),
    typeAcknowledgement("coapMessages/CoAPMessageDummyTypeAcknowledgement.bin"),
    typeReset("coapMessages/CoAPMessageDummyTypeReset.bin"),

    codeRequestGet("coapMessages/CoAPMessageDummyCodeRequestGet.bin"),
    codeRequestPost("coapMessages/CoAPMessageDummyCodeRequestPost.bin"),
    codeRequestPut("coapMessages/CoAPMessageDummyCodeRequestPut.bin"),
    codeRequestDelete("coapMessages/CoAPMessageDummyCodeRequestDelete.bin"),

    codeResponseEmpty("coapMessages/CoAPMessageDummyCodeResponseEmpty.bin"),
    codeResponseCreated("coapMessages/CoAPMessageDummyCodeResponseCreated.bin"),
    codeResponseDeleted("coapMessages/CoAPMessageDummyCodeResponseDeleted.bin"),
    codeResponseValid("coapMessages/CoAPMessageDummyCodeResponseValid.bin"),
    codeResponseChanged("coapMessages/CoAPMessageDummyCodeResponseChanged.bin"),
    codeResponseContent("coapMessages/CoAPMessageDummyCodeResponseContent.bin"),
    codeResponseBadRequest("coapMessages/CoAPMessageDummyCodeResponseBadRequest.bin"),
    codeResponseUnauthorized("coapMessages/CoAPMessageDummyCodeResponseUnauthorized.bin"),
    codeResponseBadOption("coapMessages/CoAPMessageDummyCodeResponseBadOption.bin"),
    codeResponseForbidden("coapMessages/CoAPMessageDummyCodeResponseForbidden.bin"),
    codeResponseNotFound("coapMessages/CoAPMessageDummyCodeResponseNotFound.bin"),
    codeResponseMethodNotAllowed("coapMessages/CoAPMessageDummyCodeResponseMethodNotAllowed.bin"),
    codeResponseNotAcceptable("coapMessages/CoAPMessageDummyCodeResponseNotAcceptable.bin"),
    codeResponsePreconditionFailed("coapMessages/CoAPMessageDummyCodeResponsePreconditionFailed.bin"),
    codeResponseRequestEntityTooLarge("coapMessages/CoAPMessageDummyCodeResponseRequestEntityTooLarge.bin"),
    codeResponseUnsupportedContentFormat("coapMessages/CoAPMessageDummyCodeResponseUnsupportedContentFormat.bin"),
    codeResponseInternalServerError("coapMessages/CoAPMessageDummyCodeResponseInternalServerError.bin"),
    codeResponseNotImplemented("coapMessages/CoAPMessageDummyCodeResponseNotImplemented.bin"),
    codeResponseBadGateway("coapMessages/CoAPMessageDummyCodeResponseBadGateway.bin"),
    codeResponseServiceUnavailable("coapMessages/CoAPMessageDummyCodeResponseServiceUnavailable.bin"),
    codeResponseGatewayTimeout("coapMessages/CoAPMessageDummyCodeResponseGatewayTimeout.bin"),
    codeResponseProxyingNotSupported("coapMessages/CoAPMessageDummyCodeResponseProxyingNotSupported.bin"),
    codeResponseContinued("coapMessages/CoAPMessageDummyCodeResponseContinued.bin"),
    codeResponseRequestEntityIncomplete("coapMessages/CoAPMessageDummyCodeResponseRequestEntityIncomplete.bin"),
    codeResponseInvalidCode("coapMessages/CoAPMessageDummyCodeResponseInvalidCode.bin"),

    optionsAllPossible("coapMessages/CoAPMessageDummyOptionsAllPossible.bin"),
    optionsRepeatable("coapMessages/CoAPMessageDummyOptionsRepeatable.bin"),
    optionsNoneRepeatable("coapMessages/CoAPMessageDummyOptionsNoneRepeatable.bin"),
    optionsIntValue("coapMessages/CoAPMessageDummyOptionsIntValue.bin"),
    optionsMaxIntValue("coapMessages/CoAPMessageDummyOptionsMaxIntValue.bin"),
    optionsMinIntValue("coapMessages/CoAPMessageDummyOptionsMinIntValue.bin"),
    optionsStringValue("coapMessages/CoAPMessageDummyOptionsStringValue.bin"),
    optionsDataValue("coapMessages/CoAPMessageDummyOptionsDataValue.bin"),

    payloadData("coapMessages/CoAPMessageDummyPayloadData.bin"),
    payloadString("coapMessages/CoAPMessageDummyPayloadString.bin"),

    validCoapVersion("coapMessages/CoAPMessageDummyValidCoapVersion.bin"),
    invalidCoapVersion("coapMessages/CoAPMessageDummyInvalidCoapVersion.bin"),

    ;

    fun read(): ByteArray {
        /*val byteArr = File(
                javaClass.classLoader
                        ?.getResource(fileName)
                        ?.file
        ).readBytes()
        println("1:${byteArr.contentToString()}, byteStr:${String(byteArr)} path:$fileName")*/
        return File(
                javaClass.classLoader
                        ?.getResource(fileName)
                        ?.file
        ).readBytes()
    }
}