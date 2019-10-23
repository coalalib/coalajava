package com.ndmsystems.coala

import java.io.File

/**
 * Created by Toukhvatullin Marat on 21.10.2019.
 */
enum class DummyData(private val fileName: String) {
    CoapUrl("coapMessages/CoAPMessageDummyCoapUrl.bin"),
    CoapsUrl("coapMessages/CoAPMessageDummyCoapsUrl.bin"),

    MessageIdRegular("coapMessages/CoAPMessageDummyMessageIdRegular.bin"),
    MessageIdMin("coapMessages/CoAPMessageDummyMessageIdMin.bin"),
    MessageIdMax("coapMessages/CoAPMessageDummyMessageIdMax.bin"),

    AbsentToken("coapMessages/CoAPMessageDummyAbsentToken.bin"),
    ExistingToken("coapMessages/CoAPMessageDummyExistingToken.bin"),

    TypeConfirmable("coapMessages/CoAPMessageDummyTypeConfirmable.bin"),
    TypeNonConfirmable("coapMessages/CoAPMessageDummyTypeNonConfirmable.bin"),
    TypeAcknowledgement("coapMessages/CoAPMessageDummyTypeAcknowledgement.bin"),
    TypeReset("coapMessages/CoAPMessageDummyTypeReset.bin"),

    CodeRequestGet("coapMessages/CoAPMessageDummyCodeRequestGet.bin"),
    CodeRequestPost("coapMessages/CoAPMessageDummyCodeRequestPost.bin"),
    CodeRequestPut("coapMessages/CoAPMessageDummyCodeRequestPut.bin"),
    CodeRequestDelete("coapMessages/CoAPMessageDummyCodeRequestDelete.bin"),

    CodeResponseEmpty("coapMessages/CoAPMessageDummyCodeResponseEmpty.bin"),
    CodeResponseCreated("coapMessages/CoAPMessageDummyCodeResponseCreated.bin"),
    CodeResponseDeleted("coapMessages/CoAPMessageDummyCodeResponseDeleted.bin"),
    CodeResponseValid("coapMessages/CoAPMessageDummyCodeResponseValid.bin"),
    CodeResponseChanged("coapMessages/CoAPMessageDummyCodeResponseChanged.bin"),
    CodeResponseContent("coapMessages/CoAPMessageDummyCodeResponseContent.bin"),
    CodeResponseBadRequest("coapMessages/CoAPMessageDummyCodeResponseBadRequest.bin"),
    CodeResponseUnauthorized("coapMessages/CoAPMessageDummyCodeResponseUnauthorized.bin"),
    CodeResponseBadOption("coapMessages/CoAPMessageDummyCodeResponseBadOption.bin"),
    CodeResponseForbidden("coapMessages/CoAPMessageDummyCodeResponseForbidden.bin"),
    CodeResponseNotFound("coapMessages/CoAPMessageDummyCodeResponseNotFound.bin"),
    CodeResponseMethodNotAllowed("coapMessages/CoAPMessageDummyCodeResponseMethodNotAllowed.bin"),
    CodeResponseNotAcceptable("coapMessages/CoAPMessageDummyCodeResponseNotAcceptable.bin"),
    CodeResponsePreconditionFailed("coapMessages/CoAPMessageDummyCodeResponsePreconditionFailed.bin"),
    CodeResponseRequestEntityTooLarge("coapMessages/CoAPMessageDummyCodeResponseRequestEntityTooLarge.bin"),
    CodeResponseUnsupportedContentFormat("coapMessages/CoAPMessageDummyCodeResponseUnsupportedContentFormat.bin"),
    CodeResponseInternalServerError("coapMessages/CoAPMessageDummyCodeResponseInternalServerError.bin"),
    CodeResponseNotImplemented("coapMessages/CoAPMessageDummyCodeResponseNotImplemented.bin"),
    CodeResponseBadGateway("coapMessages/CoAPMessageDummyCodeResponseBadGateway.bin"),
    CodeResponseServiceUnavailable("coapMessages/CoAPMessageDummyCodeResponseServiceUnavailable.bin"),
    CodeResponseGatewayTimeout("coapMessages/CoAPMessageDummyCodeResponseGatewayTimeout.bin"),
    CodeResponseProxyingNotSupported("coapMessages/CoAPMessageDummyCodeResponseProxyingNotSupported.bin"),
    CodeResponseContinued("coapMessages/CoAPMessageDummyCodeResponseContinued.bin"),
    CodeResponseRequestEntityIncomplete("coapMessages/CoAPMessageDummyCodeResponseRequestEntityIncomplete.bin"),
    CodeResponseInvalidCode("coapMessages/CoAPMessageDummyCodeResponseInvalidCode.bin"),

    OptionsAllPossible("coapMessages/CoAPMessageDummyOptionsAllPossible.bin"),
    OptionsRepeatable("coapMessages/CoAPMessageDummyOptionsRepeatable.bin"),
    OptionsNoneRepeatable("coapMessages/CoAPMessageDummyOptionsNoneRepeatable.bin"),
    OptionsIntValue("coapMessages/CoAPMessageDummyOptionsIntValue.bin"),
    OptionsMaxIntValue("coapMessages/CoAPMessageDummyOptionsMaxIntValue.bin"),
    OptionsMinIntValue("coapMessages/CoAPMessageDummyOptionsMinIntValue.bin"),
    OptionsStringValue("coapMessages/CoAPMessageDummyOptionsStringValue.bin"),
    OptionsDataValue("coapMessages/CoAPMessageDummyOptionsDataValue.bin"),

    PayloadData("coapMessages/CoAPMessageDummyPayloadData.bin"),
    PayloadString("coapMessages/CoAPMessageDummyPayloadString.bin"),

    ValidCoapVersion("coapMessages/CoAPMessageDummyValidCoapVersion.bin"),
    InvalidCoapVersion("coapMessages/CoAPMessageDummyInvalidCoapVersion.bin"),

    ;

    fun read(): ByteArray {
        val byteArr = File(
                javaClass.classLoader
                        ?.getResource(fileName)
                        ?.file
        ).readBytes()
        println("1:${byteArr.contentToString()}, byteStr:${String(byteArr)} path:$fileName")
        return File(
                javaClass.classLoader
                        ?.getResource(fileName)
                        ?.file
        ).readBytes()
    }
}