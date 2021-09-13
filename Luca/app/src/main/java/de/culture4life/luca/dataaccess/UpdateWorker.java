package de.culture4life.luca.dataaccess;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;
import androidx.work.rxjava3.RxWorker;

import de.culture4life.luca.LucaApplication;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class UpdateWorker extends RxWorker {

    public UpdateWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Single<Result> createWork() {
        return Completable.defer(() -> {
            LucaApplication application = (LucaApplication) getApplicationContext();
            DataAccessManager dataAccessManager = application.getDataAccessManager();
            return dataAccessManager.initialize(application)
                    .andThen(dataAccessManager.updateIfNecessary())
                    .subscribeOn(Schedulers.io());
        }).andThen(Single.just(Result.success()))
                .onErrorReturnItem(Result.failure());
    }

}
