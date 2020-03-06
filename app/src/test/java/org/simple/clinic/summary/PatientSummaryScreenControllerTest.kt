package org.simple.clinic.summary

import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.clearInvocations
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.ofType
import io.reactivex.subjects.PublishSubject
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.simple.clinic.analytics.Analytics
import org.simple.clinic.analytics.MockAnalyticsReporter
import org.simple.clinic.bloodsugar.BloodSugarRepository
import org.simple.clinic.bp.BloodPressureRepository
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.overdue.Appointment.Status.Cancelled
import org.simple.clinic.overdue.AppointmentCancelReason
import org.simple.clinic.overdue.AppointmentCancelReason.InvalidPhoneNumber
import org.simple.clinic.overdue.AppointmentRepository
import org.simple.clinic.patient.PatientMocker
import org.simple.clinic.patient.PatientPhoneNumber
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.patient.businessid.Identifier
import org.simple.clinic.patient.businessid.Identifier.IdentifierType.BpPassport
import org.simple.clinic.summary.AppointmentSheetOpenedFrom.BACK_CLICK
import org.simple.clinic.summary.AppointmentSheetOpenedFrom.DONE_CLICK
import org.simple.clinic.summary.OpenIntention.LinkIdWithPatient
import org.simple.clinic.summary.OpenIntention.ViewExistingPatient
import org.simple.clinic.summary.OpenIntention.ViewNewPatient
import org.simple.clinic.summary.PatientSummaryScreenControllerTest.GoBackToScreen.HOME
import org.simple.clinic.summary.PatientSummaryScreenControllerTest.GoBackToScreen.PREVIOUS
import org.simple.clinic.summary.addphone.MissingPhoneReminderRepository
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.Just
import org.simple.clinic.util.None
import org.simple.clinic.util.Optional
import org.simple.clinic.util.RxErrorsRule
import org.simple.clinic.util.scheduler.TrampolineSchedulersProvider
import org.simple.clinic.widgets.UiEvent
import org.simple.mobius.migration.MobiusTestFixture
import org.threeten.bp.Duration
import org.threeten.bp.Instant
import java.util.UUID

@RunWith(JUnitParamsRunner::class)
class PatientSummaryScreenControllerTest {

  @get:Rule
  val rxErrorsRule = RxErrorsRule()

  private val ui = mock<PatientSummaryScreenUi>()
  private val uiActions = mock<PatientSummaryUiActions>()
  private val patientRepository = mock<PatientRepository>()
  private val bpRepository = mock<BloodPressureRepository>()
  private val appointmentRepository = mock<AppointmentRepository>()
  private val patientUuid = UUID.fromString("d2fe1916-b76a-4bb6-b7e5-e107f00c3163")
  private val missingPhoneReminderRepository = mock<MissingPhoneReminderRepository>()
  private val userSession = mock<UserSession>()
  private val facilityRepository = mock<FacilityRepository>()
  private val user = PatientMocker.loggedInUser(UUID.fromString("3002c0e2-01ce-4053-833c-bc6f3aa3e3d4"))
  private val bloodSugarRepository = mock<BloodSugarRepository>()

  private val uiEvents = PublishSubject.create<UiEvent>()
  private val reporter = MockAnalyticsReporter()
  private val viewRenderer = PatientSummaryViewRenderer(ui)

  private lateinit var testFixture: MobiusTestFixture<PatientSummaryModel, PatientSummaryEvent, PatientSummaryEffect>

  @Before
  fun setUp() {
    whenever(patientRepository.patientProfileImmediate(patientUuid)) doReturn None
    whenever(patientRepository.phoneNumber(patientUuid)).doReturn(Observable.never())
    whenever(appointmentRepository.lastCreatedAppointmentForPatient(patientUuid)) doReturn None
    whenever(missingPhoneReminderRepository.hasShownReminderFor(patientUuid)).doReturn(Single.never())
    whenever(userSession.loggedInUserImmediate()).doReturn(user)
    whenever(facilityRepository.currentFacility(user)).doReturn(Observable.never())

    Analytics.addReporter(reporter)
  }

  @After
  fun tearDown() {
    Analytics.clearReporters()
    reporter.clear()
    testFixture.dispose()
  }

  @Test
  @Parameters(method = "patient summary open intentions")
  fun `when the screen is opened, the viewed patient analytics event must be sent`(openIntention: OpenIntention) {
    startMobiusLoop(openIntention)

    val expectedEvent = MockAnalyticsReporter.Event("ViewedPatient", mapOf(
        "patientId" to patientUuid.toString(),
        "from" to openIntention.analyticsName()
    ))
    assertThat(reporter.receivedEvents).contains(expectedEvent)
  }

  @Test
  @Parameters(method = "appointment cancelation reasons")
  fun `when patient's phone was marked as invalid after the phone number was last updated then update phone dialog should be shown`(
      openIntention: OpenIntention,
      cancelReason: AppointmentCancelReason
  ) {
    val canceledAppointment = PatientMocker.appointment(status = Cancelled, cancelReason = cancelReason)
    whenever(appointmentRepository.lastCreatedAppointmentForPatient(patientUuid)) doReturn Just(canceledAppointment)

    val phoneNumber = PatientMocker.phoneNumber(
        patientUuid = patientUuid,
        updatedAt = canceledAppointment.updatedAt - Duration.ofHours(2))
    whenever(patientRepository.phoneNumber(patientUuid)).doReturn(Observable.just<Optional<PatientPhoneNumber>>(Just(phoneNumber)))

    startMobiusLoop(openIntention)

    if (cancelReason == InvalidPhoneNumber) {
      verify(uiActions).showUpdatePhoneDialog(patientUuid)
    } else {
      verify(uiActions, never()).showUpdatePhoneDialog(patientUuid)
    }
  }

  @Test
  @Parameters(method = "appointment cancelation reasons")
  fun `when patient's phone was marked as invalid before the phone number was last updated then update phone dialog should not be shown`(
      openIntention: OpenIntention,
      cancelReason: AppointmentCancelReason
  ) {
    val canceledAppointment = PatientMocker.appointment(status = Cancelled, cancelReason = cancelReason)
    whenever(appointmentRepository.lastCreatedAppointmentForPatient(patientUuid)) doReturn Just(canceledAppointment)

    val phoneNumber = PatientMocker.phoneNumber(
        patientUuid = patientUuid,
        updatedAt = canceledAppointment.updatedAt + Duration.ofHours(2))
    whenever(patientRepository.phoneNumber(patientUuid)).doReturn(Observable.just<Optional<PatientPhoneNumber>>(Just(phoneNumber)))

    startMobiusLoop(openIntention)

    verify(uiActions, never()).showUpdatePhoneDialog(patientUuid)
  }

  @Suppress("unused")
  fun `appointment cancelation reasons`() =
      AppointmentCancelReason
          .values()
          .map { listOf(randomPatientSummaryOpenIntention(), it) }

  @Test
  @Parameters(method = "patient summary open intentions")
  fun `when a canceled appointment with the patient does not exist then update phone dialog should not be shown`(
      openIntention: OpenIntention
  ) {
    whenever(appointmentRepository.lastCreatedAppointmentForPatient(patientUuid)) doReturn None

    startMobiusLoop(openIntention)

    verify(uiActions, never()).showUpdatePhoneDialog(patientUuid)
  }

  @Test
  fun `when a new patient is missing a phone number, then avoid showing update phone dialog`() {
    whenever(appointmentRepository.lastCreatedAppointmentForPatient(patientUuid)) doReturn None
    whenever(patientRepository.phoneNumber(patientUuid)).doReturn(Observable.just<Optional<PatientPhoneNumber>>(None))

    startMobiusLoop(ViewExistingPatient)

    verify(uiActions, never()).showUpdatePhoneDialog(patientUuid)
  }

  @Test
  @Parameters(method = "patient summary open intentions except new patient")
  fun `when an existing patient is missing a phone number, a BP is recorded, and the user has never been reminded, then add phone dialog should be shown`(
      openIntention: OpenIntention
  ) {
    whenever(patientRepository.phoneNumber(patientUuid)).doReturn(Observable.just<Optional<PatientPhoneNumber>>(None))
    whenever(missingPhoneReminderRepository.hasShownReminderFor(patientUuid)).doReturn(Single.just(false))
    whenever(missingPhoneReminderRepository.markReminderAsShownFor(patientUuid)).doReturn(Completable.complete())

    startMobiusLoop(openIntention)
    uiEvents.onNext(PatientSummaryBloodPressureSaved)

    verify(uiActions).showAddPhoneDialog(patientUuid)
    verify(missingPhoneReminderRepository).markReminderAsShownFor(patientUuid)
  }

  @Test
  @Parameters(method = "patient summary open intentions except new patient")
  fun `when an existing patient is missing a phone number, a BP hasn't been recorded yet, and the user has never been reminded, then add phone dialog should not be shown`(
      openIntention: OpenIntention
  ) {
    whenever(patientRepository.phoneNumber(patientUuid)).doReturn(Observable.just<Optional<PatientPhoneNumber>>(None))
    whenever(missingPhoneReminderRepository.hasShownReminderFor(patientUuid)).doReturn(Single.just(false))
    whenever(missingPhoneReminderRepository.markReminderAsShownFor(patientUuid)).doReturn(Completable.complete())

    startMobiusLoop(openIntention)

    verify(uiActions, never()).showAddPhoneDialog(patientUuid)
    verify(missingPhoneReminderRepository, never()).markReminderAsShownFor(any())
  }

  @Test
  @Parameters(method = "patient summary open intentions except new patient")
  fun `when an existing patient is missing a phone number, and the user has been reminded before, then add phone dialog should not be shown`(
      openIntention: OpenIntention
  ) {
    whenever(patientRepository.phoneNumber(patientUuid)).doReturn(Observable.just<Optional<PatientPhoneNumber>>(None))
    whenever(missingPhoneReminderRepository.hasShownReminderFor(patientUuid)).doReturn(Single.just(true))

    startMobiusLoop(openIntention)

    verify(uiActions, never()).showAddPhoneDialog(patientUuid)
    verify(missingPhoneReminderRepository, never()).markReminderAsShownFor(any())
  }

  @Test
  @Parameters(method = "patient summary open intentions except new patient")
  fun `when an existing patient has a phone number, then add phone dialog should not be shown`(openIntention: OpenIntention) {
    val phoneNumber = Just(PatientMocker.phoneNumber(number = "101"))
    whenever(patientRepository.phoneNumber(patientUuid)).doReturn(Observable.just<Optional<PatientPhoneNumber>>(phoneNumber))
    whenever(missingPhoneReminderRepository.hasShownReminderFor(patientUuid)).doReturn(Single.never())

    startMobiusLoop(openIntention)

    verify(uiActions, never()).showAddPhoneDialog(patientUuid)
    verify(missingPhoneReminderRepository, never()).markReminderAsShownFor(any())
  }

  @Test
  @Parameters(method = "patient summary open intentions except new patient")
  fun `when a new patient has a phone number, then add phone dialog should not be shown`(openIntention: OpenIntention) {
    val phoneNumber = Just(PatientMocker.phoneNumber(number = "101"))
    whenever(patientRepository.phoneNumber(patientUuid)).doReturn(Observable.just<Optional<PatientPhoneNumber>>(phoneNumber))
    whenever(missingPhoneReminderRepository.hasShownReminderFor(patientUuid)).doReturn(Single.never())

    startMobiusLoop(openIntention)

    verify(uiActions, never()).showAddPhoneDialog(patientUuid)
    verify(missingPhoneReminderRepository, never()).markReminderAsShownFor(any())
  }

  @Test
  @Parameters(method = "patient summary open intentions except new patient")
  fun `when a new patient is missing a phone number, then add phone dialog should not be shown`(openIntention: OpenIntention) {
    whenever(patientRepository.phoneNumber(patientUuid)).doReturn(Observable.just<Optional<PatientPhoneNumber>>(None))
    whenever(missingPhoneReminderRepository.hasShownReminderFor(patientUuid)).doReturn(Single.just(false))

    startMobiusLoop(openIntention)

    verify(uiActions, never()).showAddPhoneDialog(patientUuid)
    verify(missingPhoneReminderRepository, never()).markReminderAsShownFor(any())
  }

  private fun randomPatientSummaryOpenIntention() = `patient summary open intentions`().shuffled().first()

  @Suppress("Unused")
  private fun `patient summary open intentions`() = listOf(
      ViewExistingPatient,
      ViewNewPatient,
      LinkIdWithPatient(Identifier("06293b71-0f56-45dc-845e-c05ee4d74153", BpPassport))
  )

  @Suppress("Unused")
  private fun `patient summary open intentions and screen to go back`(): List<List<Any>> {
    fun testCase(openIntention: OpenIntention, goBackToScreen: GoBackToScreen): List<Any> {
      return listOf(openIntention, goBackToScreen)
    }

    return listOf(
        testCase(openIntention = ViewExistingPatient, goBackToScreen = PREVIOUS),
        testCase(openIntention = ViewNewPatient, goBackToScreen = HOME),
        testCase(openIntention = LinkIdWithPatient(Identifier("06293b71-0f56-45dc-845e-c05ee4d74153", BpPassport)), goBackToScreen = HOME)
    )
  }

  @Suppress("Unused")
  private fun `patient summary open intentions except new patient`() = listOf(
      ViewExistingPatient,
      LinkIdWithPatient(Identifier("06293b71-0f56-45dc-845e-c05ee4d74153", BpPassport))
  )

  @Suppress("Unused")
  private fun `patient summary open intentions and summary item changed`(): List<List<Any>> {
    val identifier = Identifier("06293b71-0f56-45dc-845e-c05ee4d74153", BpPassport)

    return listOf(
        listOf(ViewExistingPatient, true),
        listOf(ViewExistingPatient, false),
        listOf(ViewNewPatient, true),
        listOf(ViewNewPatient, false),
        listOf(LinkIdWithPatient(identifier), true),
        listOf(LinkIdWithPatient(identifier), false))
  }


  @Test
  @Parameters(method = "params for testing link id with patient bottom sheet")
  fun `link id with patient bottom sheet should only open when patient summary is created with link id intent`(
      openIntention: OpenIntention,
      shouldShowLinkIdSheet: Boolean,
      identifier: Identifier?
  ) {
    startMobiusLoop(openIntention)

    if (shouldShowLinkIdSheet) {
      verify(uiActions).showLinkIdWithPatientView(patientUuid, identifier!!)
    } else {
      verify(uiActions, never()).showLinkIdWithPatientView(any(), any())
    }
  }

  @Suppress("Unused")
  private fun `params for testing link id with patient bottom sheet`(): List<Any> {
    val identifier = Identifier("1f79f976-f1bc-4c8a-8a53-ad646ce09fdb", BpPassport)

    return listOf(
        listOf(LinkIdWithPatient(identifier), true, identifier),
        listOf(ViewExistingPatient, false, null),
        listOf(ViewNewPatient, false, null)
    )
  }

  @Test
  fun `when the link id with patient is cancelled, the patient summary screen must be closed`() {
    startMobiusLoop(LinkIdWithPatient(Identifier("abcd", BpPassport)))

    uiEvents.onNext(PatientSummaryLinkIdCancelled)

    verify(uiActions).goToPreviousScreen()
  }

  @Test
  fun `when the link id with patient is completed, the link id screen must be closed`() {
    val openIntention = LinkIdWithPatient(identifier = Identifier("id", BpPassport))
    startMobiusLoop(openIntention)

    uiEvents.onNext(PatientSummaryLinkIdCompleted)

    verify(uiActions).hideLinkIdWithPatientView()
    verify(uiActions, never()).goToPreviousScreen()
  }

  enum class GoBackToScreen {
    HOME,
    PREVIOUS
  }

  @Test
  @Parameters(method = "patient summary open intentions")
  fun `when there are patient summary changes and at least one BP is present, clicking on back must show the schedule appointment sheet`(
      openIntention: OpenIntention
  ) {
    whenever(patientRepository.hasPatientDataChangedSince(any(), any())).doReturn(true)
    whenever(bpRepository.bloodPressureCountImmediate(patientUuid)).doReturn(1)

    val screenCreatedTimestamp = Instant.parse("2018-01-01T00:00:00Z")
    startMobiusLoop(openIntention)
    uiEvents.onNext(PatientSummaryBackClicked(patientUuid, screenCreatedTimestamp))

    verify(uiActions, never()).goToPreviousScreen()
    verify(uiActions, never()).goToHomeScreen()
    verify(uiActions).showScheduleAppointmentSheet(patientUuid, BACK_CLICK)
  }

  @Test
  @Parameters(method = "params for going back or home when clicking back when there are no BPs")
  fun `when there are patient summary changes and all bps are deleted, clicking on back must go back`(
      openIntention: OpenIntention,
      goBackToScreen: GoBackToScreen
  ) {
    val screenCreatedTimestamp = Instant.parse("2018-01-01T00:00:00Z")
    whenever(bpRepository.bloodPressureCountImmediate(patientUuid)) doReturn 0
    whenever(patientRepository.hasPatientDataChangedSince(patientUuid, screenCreatedTimestamp)) doReturn true

    startMobiusLoop(openIntention = openIntention)
    uiEvents.onNext(PatientSummaryBackClicked(patientUuid, screenCreatedTimestamp))

    verify(uiActions, never()).showScheduleAppointmentSheet(patientUuid, BACK_CLICK)
    if (goBackToScreen == HOME) {
      verify(uiActions).goToHomeScreen()
    } else {
      verify(uiActions).goToPreviousScreen()
    }
  }

  @Suppress("Unused")
  private fun `params for going back or home when clicking back when there are no BPs`(): List<List<Any>> {
    return listOf(
        listOf(
            ViewExistingPatient,
            PREVIOUS
        ),
        listOf(
            ViewNewPatient,
            HOME
        ),
        listOf(
            LinkIdWithPatient(Identifier("1f79f976-f1bc-4c8a-8a53-ad646ce09fdb", BpPassport)),
            HOME
        )
    )
  }

  @Test
  @Parameters(method = "patient summary open intentions and screen to go back")
  fun `when there are no patient summary changes and all bps are not deleted, clicking on back must go back`(
      openIntention: OpenIntention,
      goBackToScreen: GoBackToScreen
  ) {
    val screenCreatedTimestamp = Instant.parse("2018-01-01T00:00:00Z")
    whenever(bpRepository.bloodPressureCountImmediate(patientUuid)) doReturn 1
    whenever(patientRepository.hasPatientDataChangedSince(patientUuid, screenCreatedTimestamp)) doReturn false

    startMobiusLoop(openIntention = openIntention)
    uiEvents.onNext(PatientSummaryBackClicked(patientUuid, screenCreatedTimestamp))

    verify(uiActions, never()).showScheduleAppointmentSheet(patientUuid, BACK_CLICK)
    if (goBackToScreen == HOME) {
      verify(uiActions).goToHomeScreen()
    } else {
      verify(uiActions).goToPreviousScreen()
    }
  }

  @Test
  @Parameters(method = "patient summary open intentions and screen to go back")
  fun `when there are no patient summary changes and all bps are deleted, clicking on back must go back`(
      openIntention: OpenIntention,
      goBackToScreen: GoBackToScreen
  ) {
    val screenCreatedTimestamp = Instant.parse("2018-01-01T00:00:00Z")
    whenever(bpRepository.bloodPressureCountImmediate(patientUuid)) doReturn 0
    whenever(patientRepository.hasPatientDataChangedSince(patientUuid, screenCreatedTimestamp)) doReturn false

    startMobiusLoop(openIntention = openIntention)
    uiEvents.onNext(PatientSummaryBackClicked(patientUuid, screenCreatedTimestamp))

    verify(uiActions, never()).showScheduleAppointmentSheet(patientUuid, BACK_CLICK)
    if (goBackToScreen == HOME) {
      verify(uiActions).goToHomeScreen()
    } else {
      verify(uiActions).goToPreviousScreen()
    }
  }

  @Test
  @Parameters(method = "patient summary open intentions")
  fun `when all bps are not deleted, clicking on save must show the schedule appointment sheet regardless of summary changes`(
      openIntention: OpenIntention
  ) {
    whenever(bpRepository.bloodPressureCountImmediate(patientUuid)).doReturn(1)

    startMobiusLoop(openIntention)
    uiEvents.onNext(PatientSummaryDoneClicked(patientUuid))

    verify(uiActions).showScheduleAppointmentSheet(patientUuid, DONE_CLICK)
    verify(uiActions, never()).goToHomeScreen()
    verify(uiActions, never()).goToPreviousScreen()
  }

  @Test
  @Parameters(method = "patient summary open intentions")
  fun `when all bps are deleted, clicking on save must go to the home screen regardless of summary changes`(
      openIntention: OpenIntention
  ) {
    whenever(bpRepository.bloodPressureCountImmediate(patientUuid)).doReturn(0)

    startMobiusLoop(openIntention)
    uiEvents.onNext(PatientSummaryDoneClicked(patientUuid))

    verify(uiActions, never()).showScheduleAppointmentSheet(patientUuid, DONE_CLICK)
    verify(uiActions, never()).goToPreviousScreen()
    verify(uiActions).goToHomeScreen()
  }

  @Test
  fun `when schedule appointment sheet is closed after clicking back from a new patient, go to home screen`() {
    // given
    val screenCreatedTimestamp = Instant.parse("2018-01-01T00:00:00Z")
    whenever(bpRepository.bloodPressureCountImmediate(patientUuid)) doReturn 1
    whenever(patientRepository.hasPatientDataChangedSince(patientUuid, screenCreatedTimestamp)) doReturn true

    // when
    startMobiusLoop(ViewNewPatient)
    uiEvents.onNext(PatientSummaryBackClicked(patientUuid, screenCreatedTimestamp))

    verify(uiActions).showScheduleAppointmentSheet(patientUuid, BACK_CLICK)
    verifyNoMoreInteractions(ui)
    clearInvocations(ui)

    uiEvents.onNext(ScheduleAppointmentSheetClosed(BACK_CLICK))

    // then
    verify(uiActions).goToHomeScreen()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when schedule appointment sheet is closed after clicking back from an existing patient, go to previous screen`() {
    // given
    val screenCreatedTimestamp = Instant.parse("2018-01-01T00:00:00Z")
    whenever(bpRepository.bloodPressureCountImmediate(patientUuid)) doReturn 1
    whenever(patientRepository.hasPatientDataChangedSince(patientUuid, screenCreatedTimestamp)) doReturn true

    // when
    startMobiusLoop(ViewExistingPatient)
    uiEvents.onNext(PatientSummaryBackClicked(patientUuid, screenCreatedTimestamp))

    verify(uiActions).showScheduleAppointmentSheet(patientUuid, BACK_CLICK)
    verifyNoMoreInteractions(ui)
    clearInvocations(ui)

    uiEvents.onNext(ScheduleAppointmentSheetClosed(BACK_CLICK))

    // then
    verify(uiActions).goToPreviousScreen()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when schedule appointment sheet is closed after clicking back after linking an ID with existing patient, go to home screen`() {
    // given
    val screenCreatedTimestamp = Instant.parse("2018-01-01T00:00:00Z")
    val identifier = PatientMocker.bpPassportIdentifier()
    whenever(bpRepository.bloodPressureCountImmediate(patientUuid)) doReturn 1
    whenever(patientRepository.hasPatientDataChangedSince(patientUuid, screenCreatedTimestamp)) doReturn true

    // when
    startMobiusLoop(LinkIdWithPatient(identifier))
    uiEvents.onNext(PatientSummaryBackClicked(patientUuid, screenCreatedTimestamp))

    verify(uiActions).showLinkIdWithPatientView(patientUuid, identifier)
    verify(uiActions).showScheduleAppointmentSheet(patientUuid, BACK_CLICK)
    verifyNoMoreInteractions(ui)
    clearInvocations(ui)

    uiEvents.onNext(ScheduleAppointmentSheetClosed(BACK_CLICK))

    // then
    verify(uiActions).goToHomeScreen()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when schedule appointment sheet is closed after clicking save from a new patient, go to home screen`() {
    // given
    val screenCreatedTimestamp = Instant.parse("2018-01-01T00:00:00Z")
    whenever(bpRepository.bloodPressureCountImmediate(patientUuid)) doReturn 1
    whenever(patientRepository.hasPatientDataChangedSince(patientUuid, screenCreatedTimestamp)) doReturn true

    // when
    startMobiusLoop(ViewNewPatient)
    uiEvents.onNext(PatientSummaryDoneClicked(patientUuid))

    verify(uiActions).showScheduleAppointmentSheet(patientUuid, DONE_CLICK)
    verifyNoMoreInteractions(ui)
    clearInvocations(ui)

    uiEvents.onNext(ScheduleAppointmentSheetClosed(DONE_CLICK))

    // then
    verify(uiActions).goToHomeScreen()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when schedule appointment sheet is closed after clicking save from an existing patient, go to home screen`() {
    // given
    val screenCreatedTimestamp = Instant.parse("2018-01-01T00:00:00Z")
    whenever(bpRepository.bloodPressureCountImmediate(patientUuid)) doReturn 1
    whenever(patientRepository.hasPatientDataChangedSince(patientUuid, screenCreatedTimestamp)) doReturn true

    // when
    startMobiusLoop(ViewExistingPatient)
    uiEvents.onNext(PatientSummaryDoneClicked(patientUuid))

    verify(uiActions).showScheduleAppointmentSheet(patientUuid, DONE_CLICK)
    verifyNoMoreInteractions(ui)
    clearInvocations(ui)

    uiEvents.onNext(ScheduleAppointmentSheetClosed(DONE_CLICK))

    // then
    verify(uiActions).goToHomeScreen()
    verifyNoMoreInteractions(ui)
  }

  @Test
  fun `when schedule appointment sheet is closed after clicking save after linking an ID with existing patient, go to home screen`() {
    // given
    val screenCreatedTimestamp = Instant.parse("2018-01-01T00:00:00Z")
    val identifier = PatientMocker.bpPassportIdentifier()
    whenever(bpRepository.bloodPressureCountImmediate(patientUuid)) doReturn 1
    whenever(patientRepository.hasPatientDataChangedSince(patientUuid, screenCreatedTimestamp)) doReturn true

    // when
    startMobiusLoop(LinkIdWithPatient(identifier))
    uiEvents.onNext(PatientSummaryDoneClicked(patientUuid))

    verify(uiActions).showLinkIdWithPatientView(patientUuid, identifier)
    verify(uiActions).showScheduleAppointmentSheet(patientUuid, DONE_CLICK)
    verifyNoMoreInteractions(ui)
    clearInvocations(ui)

    uiEvents.onNext(ScheduleAppointmentSheetClosed(DONE_CLICK))

    // then
    verify(uiActions).goToHomeScreen()
    verifyNoMoreInteractions(ui)
  }

  private fun startMobiusLoop(openIntention: OpenIntention) {
    val effectHandler = PatientSummaryEffectHandler(
        schedulersProvider = TrampolineSchedulersProvider(),
        patientRepository = patientRepository,
        bloodPressureRepository = bpRepository,
        appointmentRepository = appointmentRepository,
        missingPhoneReminderRepository = missingPhoneReminderRepository,
        userSession = userSession,
        facilityRepository = facilityRepository,
        bloodSugarRepository = bloodSugarRepository,
        uiActions = uiActions
    )

    testFixture = MobiusTestFixture(
        events = uiEvents.ofType(),
        defaultModel = PatientSummaryModel.from(openIntention, patientUuid),
        init = PatientSummaryInit(),
        update = PatientSummaryUpdate(),
        effectHandler = effectHandler.build(),
        modelUpdateListener = viewRenderer::render
    )

    testFixture.start()
  }
}
