package de.culture4life.luca.ui.meeting;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ncorti.slidetoact.SlideToActView;

import de.culture4life.luca.R;
import de.culture4life.luca.ui.BaseFragment;
import de.culture4life.luca.ui.dialog.BaseDialogFragment;
import de.culture4life.luca.util.AccessibilityServiceUtil;
import io.reactivex.rxjava3.core.Completable;

import static de.culture4life.luca.ui.BaseQrCodeViewModel.BARCODE_DATA_KEY;

public class MeetingFragment extends BaseFragment<MeetingViewModel> {

    private ImageView qrCodeImageView;
    private View loadingView;
    private TextView durationTextView;
    private TextView membersTextView;
    private ImageView meetingDescriptionInfoImageView;
    private ImageView meetingMembersInfoImageView;
    private SlideToActView slideToActView;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        qrCodeImageView = view.findViewById(R.id.qrCodeImageView);
        loadingView = view.findViewById(R.id.loadingLayout);
        durationTextView = view.findViewById(R.id.durationTextView);
        membersTextView = view.findViewById(R.id.membersTextView);
        meetingDescriptionInfoImageView = view.findViewById(R.id.infoImageView);
        meetingMembersInfoImageView = view.findViewById(R.id.meetingMembersInfoImageView);
        slideToActView = view.findViewById(R.id.slideToActView);

        return view;
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.fragment_meeting;
    }

    @Override
    protected Class<MeetingViewModel> getViewModelClass() {
        return MeetingViewModel.class;
    }

    @Override
    protected Completable initializeViews() {
        return super.initializeViews()
                .andThen(Completable.fromAction(() -> {
                    observe(viewModel.getIsHostingMeeting(), isHostingMeeting -> {
                        if (!isHostingMeeting) {
                            safeNavigateFromNavController(R.id.action_meetingFragment_to_checkInFragment, viewModel.getBundle().getValue());
                        }
                        AccessibilityServiceUtil.speak(getContext(), getString(R.string.meeting_was_ended_hint));
                    });
                    observe(viewModel.getQrCode(), value -> qrCodeImageView.setImageBitmap(value));
                    observe(viewModel.getIsLoading(), loading -> loadingView.setVisibility(loading ? View.VISIBLE : View.GONE));
                    observe(viewModel.getDuration(), value -> durationTextView.setText(value));
                    observe(viewModel.getMembersCount(), value -> membersTextView.setText(value));
                    meetingDescriptionInfoImageView.setOnClickListener(v -> showMeetingDescriptionInfo());
                    meetingMembersInfoImageView.setOnClickListener(v -> showMeetingMembersInfo());
                    slideToActView.setOnSlideCompleteListener(view -> viewModel.onMeetingEndRequested());
                    slideToActView.setOnSlideUserFailedListener((view, isOutside) -> {
                        if (AccessibilityServiceUtil.isGoogleTalkbackActive(getContext())) {
                            viewModel.onMeetingEndRequested();
                        } else {
                            Toast.makeText(getContext(), R.string.venue_slider_clicked, Toast.LENGTH_SHORT).show();
                        }
                    });

                    observe(viewModel.getIsLoading(), loading -> {
                        if (!loading) {
                            slideToActView.resetSlider();
                        }
                    });
                    observe(viewModel.getBundle(), this::processBundle);
                }));
    }

    @Override
    public void onResume() {
        super.onResume();
        Bundle arguments = getArguments();
        if (arguments != null) {
            viewModel.setBundle(arguments);
        }
    }

    @Override
    public void onStop() {
        viewModel.setBundle(null);
        super.onStop();
    }

    private void showMeetingDescriptionInfo() {
        new BaseDialogFragment(new MaterialAlertDialogBuilder(getActivity())
                .setTitle(R.string.meeting_heading)
                .setMessage(R.string.meeting_description_info)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> {
                    // do nothing
                })).show();
    }

    private void showMeetingMembersInfo() {
        String count = viewModel.getMembersCount().getValue();
        String checkedIn = viewModel.getCheckedInMemberNames().getValue();
        String checkedOut = viewModel.getCheckedOutMemberNames().getValue();
        new BaseDialogFragment(new MaterialAlertDialogBuilder(getActivity())
                .setTitle(R.string.meeting_members_heading)
                .setMessage(getString(R.string.meeting_members_info, count, checkedIn, checkedOut))
                .setPositiveButton(R.string.action_ok, (dialog, which) -> {
                    // do nothing
                })).show();
    }

    private void processBundle(@Nullable Bundle bundle) {
        if (bundle == null) {
            return;
        }

        String barcode = bundle.getString(BARCODE_DATA_KEY);
        if (barcode != null) {
            // is supposed to check-in into different location
            showLocationChangeDialog();
        }
    }

    private void showLocationChangeDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.venue_change_location_title)
                .setMessage(R.string.meeting_change_location_description)
                .setPositiveButton(R.string.action_change, (dialog, which) -> viewModel.changeLocation())
                .setNegativeButton(R.string.action_cancel, (dialog, which) -> dialog.cancel())
                .setOnCancelListener(dialogInterface -> viewModel.setBundle(null));
        new BaseDialogFragment(builder).show();
    }

}
