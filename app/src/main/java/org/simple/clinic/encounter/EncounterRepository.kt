package org.simple.clinic.encounter

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.zipWith
import org.simple.clinic.AppDatabase
import org.simple.clinic.bp.BloodPressureMeasurement
import org.simple.clinic.encounter.sync.EncounterPayload
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.patient.SyncStatus.DONE
import org.simple.clinic.patient.SyncStatus.PENDING
import org.simple.clinic.patient.canBeOverriddenByServerCopy
import org.simple.clinic.sync.SynceableRepository
import org.simple.clinic.util.UserClock
import org.threeten.bp.LocalDate
import java.util.UUID
import javax.inject.Inject

class EncounterRepository @Inject constructor(
    private val database: AppDatabase,
    private val userClock: UserClock
) : SynceableRepository<ObservationsForEncounter, EncounterPayload> {

  override fun save(records: List<ObservationsForEncounter>): Completable {
    return saveObservationsForEncounters(records)
  }

  override fun recordsWithSyncStatus(syncStatus: SyncStatus): Single<List<ObservationsForEncounter>> {
    return database.encountersDao().recordsWithSyncStatus(syncStatus).firstOrError()
  }

  override fun setSyncStatus(from: SyncStatus, to: SyncStatus): Completable {
    return database.encountersDao().updateSyncStatus(from, to)
  }

  override fun setSyncStatus(ids: List<UUID>, to: SyncStatus): Completable {
    return database.encountersDao().updateSyncStatus(ids, to)
  }

  override fun mergeWithLocalData(payloads: List<EncounterPayload>): Completable {
    val payloadObservable = Observable.fromIterable(payloads)
    val encountersCanBeOverridden = payloadObservable
        .flatMap { canEncountersBeOverridden(it) }

    return payloadObservable.zipWith(encountersCanBeOverridden)
        .filter { (_, canBeOverridden) -> canBeOverridden }
        .map { (payload, _) -> payload }
        .map(::payloadToEncounters)
        .toList()
        .flatMapCompletable(::saveObservationsForEncounters)
  }

  private fun canEncountersBeOverridden(payload: EncounterPayload): Observable<Boolean> {
    return Observable.fromCallable {
      database.encountersDao()
          .getOne(payload.uuid)
          ?.syncStatus.canBeOverriddenByServerCopy()
    }
  }

  private fun saveObservationsForEncounters(records: List<ObservationsForEncounter>): Completable {
    return Completable.fromAction {
      val bloodPressures = records.flatMap { it.bloodPressures }
      val encounters = records.map { it.encounter }

      with(database) {
        runInTransaction {
          encountersDao().save(encounters)
          bloodPressureDao().save(bloodPressures)
        }
      }
    }
  }

  private fun payloadToEncounters(payload: EncounterPayload): ObservationsForEncounter {
    val bloodPressures = payload.observations.bloodPressureMeasurements.map { bps ->
      bps.toDatabaseModel(syncStatus = DONE, encounterUuid = payload.uuid)
    }
    return ObservationsForEncounter(encounter = payload.toDatabaseModel(DONE), bloodPressures = bloodPressures)
  }

  override fun recordCount(): Observable<Int> {
    return database.encountersDao().recordCount()
  }

  override fun pendingSyncRecordCount(): Observable<Int> {
    return database.encountersDao().recordCount(syncStatus = PENDING)
  }

  fun saveBloodPressureMeasurement(
      bloodPressureMeasurement: BloodPressureMeasurement,
      encounteredDate: LocalDate
  ): Completable {
    val encounter = with(bloodPressureMeasurement) {
      Encounter(
          uuid = encounterUuid,
          patientUuid = patientUuid,
          encounteredOn = encounteredDate,
          createdAt = createdAt,
          updatedAt = updatedAt,
          deletedAt = deletedAt,
          syncStatus = PENDING
      )
    }

    return saveObservationsForEncounters(listOf(ObservationsForEncounter(
        encounter = encounter,
        bloodPressures = listOf(bloodPressureMeasurement)
    )))
  }
}
