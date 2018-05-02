package com.mainstreetcode.teammate.fragments.main;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.util.DiffUtil;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.mainstreetcode.teammate.R;
import com.mainstreetcode.teammate.adapters.JoinRequestAdapter;
import com.mainstreetcode.teammate.baseclasses.HeaderedFragment;
import com.mainstreetcode.teammate.model.JoinRequest;
import com.mainstreetcode.teammate.model.Team;
import com.mainstreetcode.teammate.model.User;
import com.mainstreetcode.teammate.util.ScrollManager;
import com.mainstreetcode.teammate.viewmodel.gofers.JoinRequestGofer;

import java.util.UUID;

import io.reactivex.Flowable;

import static com.mainstreetcode.teammate.viewmodel.gofers.JoinRequestGofer.ACCEPTING;
import static com.mainstreetcode.teammate.viewmodel.gofers.JoinRequestGofer.APPROVING;
import static com.mainstreetcode.teammate.viewmodel.gofers.JoinRequestGofer.INVITING;
import static com.mainstreetcode.teammate.viewmodel.gofers.JoinRequestGofer.JOINING;
import static com.mainstreetcode.teammate.viewmodel.gofers.JoinRequestGofer.WAITING;

/**
 * Invites a Team member
 */

public class JoinRequestFragment extends HeaderedFragment<JoinRequest>
        implements
        JoinRequestAdapter.AdapterListener {

    public static final String ARG_JOIN_REQUEST = "join-request";

    private JoinRequest request;
    private JoinRequestGofer requestGofer;

    public static JoinRequestFragment inviteInstance(Team team) {
        JoinRequestFragment fragment = newInstance(JoinRequest.invite(team));
        fragment.setEnterExitTransitions();

        return fragment;
    }

    public static JoinRequestFragment joinInstance(Team team, User user) {
        JoinRequestFragment fragment = newInstance(JoinRequest.join(team, user));
        fragment.setEnterExitTransitions();

        return fragment;
    }

    public static JoinRequestFragment viewInstance(JoinRequest request) {
        JoinRequestFragment fragment = newInstance(request);
        fragment.setEnterExitTransitions();

        return fragment;
    }

    private static JoinRequestFragment newInstance(JoinRequest joinRequest) {
        JoinRequestFragment fragment = new JoinRequestFragment();
        Bundle args = new Bundle();

        args.putParcelable(ARG_JOIN_REQUEST, joinRequest);
        fragment.setArguments(args);
        fragment.setEnterExitTransitions();

        return fragment;
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    public String getStableTag() {
        String superResult = super.getStableTag();
        JoinRequest temp = getArguments().getParcelable(ARG_JOIN_REQUEST);
        User user = temp.getUser();
        String id = temp.getId();
        String userHash = user.isEmpty() ? UUID.randomUUID().toString() : temp.getUser().getId();
        String teamHash = temp.getTeam().getCity();

        return (temp != null)
                ? TextUtils.join("-", new String[]{superResult, id, userHash, teamHash})
                : superResult;
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        request = getArguments().getParcelable(ARG_JOIN_REQUEST);
        requestGofer = teamMemberViewModel.gofer(request);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_headered, container, false);

        scrollManager = ScrollManager.withRecyclerView(rootView.findViewById(R.id.model_list))
                .withAdapter(new JoinRequestAdapter(requestGofer.getItems(), this))
                .withInconsistencyHandler(this::onInconsistencyDetected)
                .withLinearLayoutManager()
                .build();

        scrollManager.getRecyclerView().requestFocus();
        return rootView;
    }

    @Override
    public void onResume() {
        User user = userViewModel.getCurrentUser();
        Team team = request.getTeam();
        disposables.add(localRoleViewModel.getRoleInTeam(user, team).subscribe(this::onRoleUpdated, defaultErrorHandler));
        refreshState();
        super.onResume();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_user_edit, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        boolean isEmpty = request.isEmpty();
        boolean canBlockUser = localRoleViewModel.hasPrivilegedRole();
        boolean canDeleteRequest = canBlockUser || requestGofer.isRequestOwner();

        MenuItem block_item = menu.findItem(R.id.action_block);
        MenuItem deleteItem = menu.findItem(R.id.action_kick);

        block_item.setVisible(!isEmpty && canBlockUser);
        deleteItem.setVisible(!isEmpty && canDeleteRequest);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_kick:
                showDeletePrompt();
                return true;
            case R.id.action_block:
                blockUser(request.getUser(), request.getTeam());
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void togglePersistentUi() {
        super.togglePersistentUi();
        setFabClickListener(this);
        setFabIcon(R.drawable.ic_check_white_24dp);
    }

    @Override
    public boolean[] insetState() {return VERTICAL;}

    @Override
    @SuppressWarnings("SimplifiableIfStatement")
    public boolean showsFab() {
        return requestGofer.showsFab(localRoleViewModel.hasPrivilegedRole());
    }

    @Override
    public void onImageClick() {}

    @Override
    protected JoinRequest getHeaderedModel() {return request;}

    @Override
    protected Flowable<DiffUtil.DiffResult> fetch(JoinRequest model) { return Flowable.empty(); }

    @Override
    protected void onModelUpdated(DiffUtil.DiffResult result) { }

    @Override
    public boolean canEditFields() {
        return requestGofer.canEditFields();
    }

    @Override
    public boolean canEditRole() {
        return requestGofer.canEditRole();
    }

    @Override
    public void onClick(View view) {
        if (view.getId() != R.id.fab) return;

        @JoinRequestGofer.JoinRequestState
        int state = requestGofer.getState();

        if (state == WAITING) return;

        if (state == APPROVING || state == ACCEPTING) completeJoinRequest(true);
        else if (request.getPosition().isInvalid()) showSnackbar(getString(R.string.select_role));
        else if (state == JOINING || state == INVITING) createJoinRequest();
    }

    private void createJoinRequest() {
        toggleProgress(true);
        disposables.add(requestGofer.joinTeam()
                .subscribe(request -> onJoinRequestSent(), defaultErrorHandler));
    }

    private void completeJoinRequest(boolean approved) {
        toggleProgress(true);
        disposables.add(requestGofer.processJoinRequest(approved)
                .subscribe(deleted -> onRequestCompleted(approved), defaultErrorHandler));
    }

    private void onJoinRequestSent() {
        scrollManager.getRecyclerView().getRecycledViewPool().clear();
        refreshState();
        toggleFab(false);
        toggleProgress(false);
        toggleBottomSheet(false);
        showSnackbar(getString(request.isTeamApproved()
                ? R.string.user_invite_sent
                : R.string.team_submitted_join_request));
    }

    private void onRequestCompleted(boolean approved) {
        int stringResource = approved ? R.string.added_user : R.string.removed_user;
        String name = request.getUser().getFirstName();
        if (!requestGofer.isRequestOwner()) showSnackbar(getString(stringResource, name));
        requireActivity().onBackPressed();
    }

    private void onRoleUpdated() {
        toggleFab(showsFab());
        requireActivity().invalidateOptionsMenu();
        scrollManager.notifyDataSetChanged();
    }

    private void showDeletePrompt() {
        User requestUser = request.getUser();
        final String prompt = requestGofer.isRequestOwner()
                ? getString(R.string.confirm_request_leave, request.getTeam().getName())
                : getString(R.string.confirm_request_drop, requestUser.getFirstName());

        new AlertDialog.Builder(requireActivity()).setTitle(prompt)
                .setPositiveButton(R.string.yes, (dialog, which) -> completeJoinRequest(false))
                .setNegativeButton(R.string.no, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void refreshState() {
        requestGofer.refresh();
        scrollManager.notifyDataSetChanged();
        toggleFab(showsFab());
        setToolbarTitle(requestGofer.getToolbarTitle(this));
    }
}
