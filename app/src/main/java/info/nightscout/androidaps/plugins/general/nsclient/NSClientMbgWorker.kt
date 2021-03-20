package info.nightscout.androidaps.plugins.general.nsclient

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.transactions.SyncTherapyEventTransaction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.nsclient.data.NSMbg
import info.nightscout.androidaps.receivers.DataWorker
import info.nightscout.androidaps.utils.extensions.therapyEventFromNsMbg
import javax.inject.Inject

class NSClientMbgWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Inject lateinit var repository: AppRepository
    @Inject lateinit var dataWorker: DataWorker
    @Inject lateinit var aapsLogger: AAPSLogger

    override fun doWork(): Result {
        var ret = Result.success()

        val mbgArray = dataWorker.pickupJSONArray(inputData.getLong(DataWorker.STORE_KEY, -1))
            ?: return Result.failure()
        for (i in 0 until mbgArray.length()) {
            val nsMbg = NSMbg(mbgArray.getJSONObject(i))
            if (!nsMbg.isValid()) continue
            repository.runTransactionForResult(SyncTherapyEventTransaction(therapyEventFromNsMbg(nsMbg)))
                .doOnError {
                    aapsLogger.error("Error while saving therapy event", it)
                    ret = Result.failure()
                }
                .blockingGet()
                .also {
                    aapsLogger.debug(LTag.DATABASE, "Saved therapy event $it")
                }
        }
        return ret
    }

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }
}