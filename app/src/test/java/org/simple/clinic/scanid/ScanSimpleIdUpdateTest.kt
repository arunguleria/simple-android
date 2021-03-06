package org.simple.clinic.scanid

import com.spotify.mobius.test.NextMatchers.hasEffects
import com.spotify.mobius.test.NextMatchers.hasModel
import com.spotify.mobius.test.NextMatchers.hasNoModel
import com.spotify.mobius.test.UpdateSpec
import com.spotify.mobius.test.UpdateSpec.assertThatNext
import org.junit.Test
import org.simple.clinic.TestData
import org.simple.clinic.patient.Patient
import org.simple.clinic.patient.businessid.Identifier
import org.simple.clinic.patient.businessid.Identifier.IdentifierType.BpPassport
import org.simple.clinic.patient.businessid.Identifier.IdentifierType.IndiaNationalHealthId
import org.simple.clinic.platform.crash.NoOpCrashReporter
import java.util.UUID

class ScanSimpleIdUpdateTest {

  private val defaultModel = ScanSimpleIdModel.create()
  private val expectedJson = """
    {
    "hidn":"28-3222-2283-6682",
    "hid":"mogithduraisamy@ndhm",
    "name":"Mogith",
    "gender":"M",
    "statelgd":"34",
    "distlgd":"600",
    "dob":"25/6/2012",
    "address":"No.42 eswaran kovil street"
     }
     """

  private val spec = UpdateSpec(ScanSimpleIdUpdate(
      crashReporter = NoOpCrashReporter()
  ))

  @Test
  fun `when a valid QR code is scanned, then search for the patient`() {
    val scannedId = "9f154761-ee2f-4ee3-acd1-0038328f75ca"
    val identifier = Identifier(scannedId, BpPassport)

    spec
        .given(defaultModel)
        .whenEvent(ScanSimpleIdScreenQrCodeScanned(scannedId))
        .then(assertThatNext(
            hasModel(defaultModel.searching()),
            hasEffects(SearchPatientByIdentifier(identifier))
        ))
  }

  @Test
  fun `when the qr code is scanned and the returned string is json then parse the json into object`() {
    spec
        .given(defaultModel)
        .whenEvent(ScanSimpleIdScreenQrCodeScanned(expectedJson))
        .then(assertThatNext(
            hasModel(defaultModel.searching()),
            hasEffects(ParseScannedJson(expectedJson))
        ))
  }

  @Test
  fun `when json is parsed then search patient with NHID`() {
    val indiaNationalHealthID = "1234123412341234"
    val indiaNHIDInfoPayload = TestData.indiaNHIDInfoPayload(
        healthIdNumber = indiaNationalHealthID
    )
    val indiaNHIDInfo = indiaNHIDInfoPayload.fromPayload()

    val identifier = Identifier(indiaNationalHealthID, IndiaNationalHealthId)

    spec
        .given(defaultModel)
        .whenEvent(ScannedQRCodeJsonParsed(indiaNHIDInfo))
        .then(assertThatNext(
            hasModel(defaultModel.searching()),
            hasEffects(SearchPatientByIdentifier(identifier))
        ))
  }

  @Test
  fun `when the entered short code is valid, send the entered short code to the parent screen`() {
    val shortCode = "1234567"
    val model = defaultModel.shortCodeChanged(EnteredCodeInput(shortCode))

    val expectedScanResult = SearchByEnteredCode(shortCode)

    spec
        .given(model)
        .whenEvent(EnteredCodeValidated(EnteredCodeValidationResult.Success))
        .then(assertThatNext(
            hasNoModel(),
            hasEffects(SendScannedIdentifierResult(expectedScanResult))
        ))
  }

  @Test
  fun `when identifier is scanned and patient is found, then send the patient id to the parent screen`() {
    val patientId = UUID.fromString("60822507-9151-4836-944b-9cbbd1530c0b")
    val patient = TestData.patient(
        uuid = patientId
    )
    val identifier = Identifier("123456", BpPassport)

    val expectedScanResult = PatientFound(patientId)

    spec
        .given(defaultModel)
        .whenEvent(PatientSearchByIdentifierCompleted(listOf(patient), identifier))
        .then(assertThatNext(
            hasModel(defaultModel.notSearching()),
            hasEffects(SendScannedIdentifierResult(expectedScanResult))
        ))
  }

  @Test
  fun `when identifier is scanned and patient is not found, then send the identifier to parent screen`() {
    val patients = emptyList<Patient>()
    val identifier = Identifier("123456", BpPassport)

    val expectedScanResult = PatientNotFound(identifier)

    spec
        .given(defaultModel)
        .whenEvent(PatientSearchByIdentifierCompleted(patients, identifier))
        .then(assertThatNext(
            hasModel(defaultModel.notSearching()),
            hasEffects(SendScannedIdentifierResult(expectedScanResult))
        ))
  }

  @Test
  fun `when identifier is scanned and more than 1 patient is found, then send the short code to parent screen`() {
    val patientId1 = UUID.fromString("60822507-9151-4836-944b-9cbbd1530c0b")
    val patientId2 = UUID.fromString("de90d491-29ab-4bb7-938c-d436815794c6")

    val patient1 = TestData.patient(uuid = patientId1)
    val patient2 = TestData.patient(uuid = patientId2)

    val identifier = Identifier("123456", BpPassport)

    val expectedScanResult = SearchByEnteredCode("123456")

    spec
        .given(defaultModel)
        .whenEvent(PatientSearchByIdentifierCompleted(listOf(patient1, patient2), identifier))
        .then(assertThatNext(
            hasModel(defaultModel.notSearching()),
            hasEffects(SendScannedIdentifierResult(expectedScanResult))
        ))
  }
}
