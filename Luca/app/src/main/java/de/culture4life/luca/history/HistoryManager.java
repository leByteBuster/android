package de.culture4life.luca.history;

import android.content.Context;

import de.culture4life.luca.Manager;
import de.culture4life.luca.checkin.CheckInData;
import de.culture4life.luca.dataaccess.AccessedTraceData;
import de.culture4life.luca.document.Document;
import de.culture4life.luca.meeting.MeetingData;
import de.culture4life.luca.meeting.MeetingGuestData;
import de.culture4life.luca.meeting.MeetingManager;
import de.culture4life.luca.preference.PreferencesManager;
import de.culture4life.luca.registration.RegistrationData;
import de.culture4life.luca.ui.venue.children.ChildListItem;
import de.culture4life.luca.ui.venue.children.ChildListItemContainer;

import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.PublishSubject;
import timber.log.Timber;

import static de.culture4life.luca.checkin.CheckInManager.KEY_CHILDREN;

public class HistoryManager extends Manager {

    public static final long MAXIMUM_CHECK_IN_DURATION = TimeUnit.DAYS.toMillis(1);
    public static final long KEEP_DATA_DURATION = TimeUnit.DAYS.toMillis(14);
    public static final String KEY_HISTORY_ITEMS = "history_items_2";

    @Deprecated
    public static final String KEY_HISTORY_ITEMS_V1 = "history_items";

    private final PreferencesManager preferencesManager;

    private final PublishSubject<HistoryItem> newItemPublisher;

    @Nullable
    private Observable<HistoryItem> cachedHistoryItems;

    public HistoryManager(@NonNull PreferencesManager preferencesManager) {
        this.preferencesManager = preferencesManager;
        this.newItemPublisher = PublishSubject.create();
    }

    @Override
    protected Completable doInitialize(@NonNull Context context) {
        return preferencesManager.initialize(context)
                .andThen(migrateOldItems())
                .andThen(deleteOldItems());
    }

    private Completable migrateOldItems() {
        // migration is not needed anymore, as not yet migrated history items
        // are older than 2 weeks by now
        return preferencesManager.delete(KEY_HISTORY_ITEMS_V1);
    }

    public Completable addCheckInItem(@NonNull CheckInData checkInData) {
        return Single.just(checkInData)
                .map(data -> {
                    HistoryItem item = new HistoryItem(HistoryItem.TYPE_CHECK_IN);
                    item.setRelatedId(checkInData.getTraceId());
                    item.setTimestamp(checkInData.getTimestamp());
                    item.setDisplayName(checkInData.getLocationDisplayName());
                    return item;
                })
                .flatMapCompletable(this::addItem);
    }

    public Completable addCheckOutItem(@NonNull CheckInData checkInData) {
        return Single.just(checkInData)
                .map(data -> {
                    CheckOutItem item = new CheckOutItem();
                    item.setRelatedId(checkInData.getTraceId());
                    item.setTimestamp(Math.min(checkInData.getTimestamp() + MAXIMUM_CHECK_IN_DURATION, System.currentTimeMillis()));
                    item.setDisplayName(checkInData.getLocationDisplayName());
                    return item;
                })
                .flatMap(this::setChildren)
                .flatMapCompletable(this::addItem);
    }

    public Completable addContactDataUpdateItem(@NonNull RegistrationData registrationData) {
        return Single.just(registrationData)
                .map(data -> {
                    HistoryItem item = new HistoryItem(HistoryItem.TYPE_CONTACT_DATA_UPDATE);
                    item.setRelatedId(registrationData.getId() != null ? registrationData.getId().toString() : null);
                    item.setDisplayName(registrationData.getFullName());
                    return item;
                })
                .flatMapCompletable(this::addItem);
    }

    public Completable addMeetingStartedItem(@NonNull MeetingData meetingData) {
        return Single.fromCallable(() -> {
            HistoryItem item = new HistoryItem(HistoryItem.TYPE_MEETING_STARTED);
            item.setRelatedId(meetingData.getLocationId().toString());
            return item;
        }).flatMapCompletable(this::addItem);
    }

    public Completable addMeetingEndedItem(@NonNull MeetingData meetingData) {
        return Single.fromCallable(() -> {
            MeetingEndedItem item = new MeetingEndedItem();
            item.setRelatedId(meetingData.getLocationId().toString());
            for (MeetingGuestData guestData : meetingData.getGuestData()) {
                item.getGuests().add(MeetingManager.getReadableGuestName(guestData));
            }
            return item;
        }).flatMapCompletable(this::addItem);
    }

    public Completable addHistoryDeletedItem() {
        return Single.fromCallable(() -> {
            HistoryItem item = new HistoryItem(HistoryItem.TYPE_DATA_DELETED);
            item.setDisplayName("");
            return item;
        }).flatMapCompletable(this::addItem);
    }

    public Completable addTraceDataAccessedItem(@NonNull AccessedTraceData accessedTraceData) {
        return Single.fromCallable(() -> {
            TraceDataAccessedItem item = new TraceDataAccessedItem();
            item.setHealthDepartmentId(accessedTraceData.getHealthDepartmentId());
            item.setTraceId(accessedTraceData.getTraceId());
            item.setRelatedId(item.getHealthDepartmentId() + ";" + item.getTraceId());
            item.setTimestamp(accessedTraceData.getAccessTimestamp());
            item.setHealthDepartmentName(accessedTraceData.getHealthDepartmentName());
            item.setLocationName(accessedTraceData.getLocationName());
            item.setCheckInTimestamp(accessedTraceData.getCheckInTimestamp());
            item.setCheckOutTimestamp(accessedTraceData.getCheckOutTimestamp());
            return item;
        }).flatMapCompletable(this::addItem);
    }

    public Completable addDataSharedItem(@NonNull String tracingTan, int days) {
        return Single.fromCallable(() -> new DataSharedItem(tracingTan, days))
                .flatMapCompletable(this::addItem);
    }

    public Completable addDocumentImportedItem(@NonNull Document document) {
        return Single.fromCallable(() -> new DocumentImportedItem(document))
                .flatMapCompletable(this::addItem);
    }

    public Completable addItem(@NonNull HistoryItem historyItem) {
        return persistItemsToPreferences(getItems()
                .mergeWith(Observable.just(historyItem)))
                .andThen(invalidateItemCache())
                .doOnComplete(() -> newItemPublisher.onNext(historyItem))
                .doOnSubscribe(disposable -> Timber.d("Adding history item: %s", historyItem));
    }

    public Observable<HistoryItem> getItems() {
        return Observable.defer(() -> {
            if (cachedHistoryItems == null) {
                cachedHistoryItems = restoreItemsFromPreferences().cache();
            }
            return cachedHistoryItems;
        });
    }

    public Observable<HistoryItem> getNewItems() {
        return newItemPublisher
                .doOnNext(historyItem -> Timber.d("New history item: %s", historyItem));
    }

    public Completable clearItems() {
        return persistItemsToPreferences(Observable.empty())
                .andThen(invalidateItemCache())
                .andThen(addHistoryDeletedItem());
    }

    private Completable deleteOldItems() {
        return Single.fromCallable(() -> System.currentTimeMillis() - KEEP_DATA_DURATION)
                .flatMapCompletable(this::deleteItemsCreatedBefore);
    }

    private Completable deleteItemsCreatedBefore(long timestamp) {
        Observable<HistoryItem> remainingItems = getItems()
                .filter(historyItem -> historyItem.getTimestamp() > timestamp);

        return persistItemsToPreferences(remainingItems)
                .doOnComplete(() -> Timber.d("Deleted history items created before %d", timestamp))
                .andThen(invalidateItemCache());
    }

    private Completable invalidateItemCache() {
        return Completable.fromAction(() -> cachedHistoryItems = null);
    }

    private Observable<HistoryItem> restoreItemsFromPreferences() {
        return preferencesManager.restoreOrDefault(KEY_HISTORY_ITEMS, new HistoryItemContainer())
                .flatMapObservable(Observable::fromIterable)
                .distinct(historyItem -> historyItem.getRelatedId() + historyItem.getType())
                .sorted((first, second) -> Long.compare(second.getTimestamp(), first.getTimestamp()))
                .doOnSubscribe(disposable -> Timber.d("Restoring items from preferences"));
    }

    private Completable persistItemsToPreferences(Observable<HistoryItem> historyItems) {
        return historyItems.toList()
                .map(HistoryItemContainer::new)
                .flatMapCompletable(historyItemContainer -> preferencesManager.persist(KEY_HISTORY_ITEMS, historyItemContainer));
    }

    private Single<CheckOutItem> setChildren(@NonNull CheckOutItem item) {
        return preferencesManager.restoreOrDefault(KEY_CHILDREN, new ChildListItemContainer())
                .flatMap(childListItems -> {
                    ChildListItemContainer checkedInChildren = new ChildListItemContainer();
                    for (ChildListItem childListItem : childListItems) {
                        if (childListItem.isChecked()) {
                            checkedInChildren.add(childListItem);
                            childListItem.toggleIsChecked();
                        }
                    }
                    return preferencesManager.persist(KEY_CHILDREN, childListItems)
                            .andThen(Single.just(checkedInChildren));
                })
                .map(ChildListItemContainer::getNames)
                .doOnSuccess(item::setChildren)
                .map(names -> item);
    }

    public static String createUnorderedList(@NonNull List<String> items) {
        if (items.isEmpty()) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (String item : items) {
            stringBuilder.append("- ").append(item).append(System.lineSeparator());
        }
        stringBuilder.deleteCharAt(stringBuilder.length() - 1); // remove last line break
        return stringBuilder.toString();
    }

}
