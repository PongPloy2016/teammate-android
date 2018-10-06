package com.mainstreetcode.teammate.viewmodel;

import android.support.v7.util.DiffUtil;

import com.mainstreetcode.teammate.model.Competitor;
import com.mainstreetcode.teammate.model.Identifiable;
import com.mainstreetcode.teammate.model.Message;
import com.mainstreetcode.teammate.model.Standings;
import com.mainstreetcode.teammate.model.Team;
import com.mainstreetcode.teammate.model.Tournament;
import com.mainstreetcode.teammate.model.enums.StatType;
import com.mainstreetcode.teammate.repository.CompetitorRepository;
import com.mainstreetcode.teammate.repository.TournamentRepository;
import com.mainstreetcode.teammate.rest.TeammateApi;
import com.mainstreetcode.teammate.rest.TeammateService;
import com.mainstreetcode.teammate.util.ModelUtils;
import com.mainstreetcode.teammate.viewmodel.events.Alert;
import com.mainstreetcode.teammate.viewmodel.gofers.TournamentGofer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;

import static io.reactivex.android.schedulers.AndroidSchedulers.mainThread;

/**
 * ViewModel for {@link Tournament tournaments}
 */

public class TournamentViewModel extends TeamMappedViewModel<Tournament> {

    private final TeammateApi api;
    private final TournamentRepository repository;
    private final Map<Tournament, Standings> standingsMap = new HashMap<>();
    private final Map<Tournament, List<Identifiable>> ranksMap = new HashMap<>();

    public TournamentViewModel() {
        api = TeammateService.getApiInstance();
        repository = TournamentRepository.getInstance();
    }

    public TournamentGofer gofer(Tournament tournament) {
        return new TournamentGofer(tournament, onError(tournament), this::getTournament, this::createOrUpdateTournament, this::delete,
                ignored -> CompetitorRepository.getInstance().modelsBefore(tournament, 0));
    }

    @Override
    Class<Tournament> valueClass() { return Tournament.class; }

    public Single<Tournament> addCompetitors(final Tournament tournament, List<Competitor> competitors) {
        return repository.addCompetitors(tournament, competitors).observeOn(mainThread());
    }

    @Override
    Flowable<List<Tournament>> fetch(Team key, boolean fetchLatest) {
        return repository.modelsBefore(key, getQueryDate(fetchLatest, key, Tournament::getCreated));
    }

    @Override
    void onErrorMessage(Message message, Team key, Identifiable invalid) {
        super.onErrorMessage(message, key, invalid);
        boolean shouldRemove = message.isInvalidObject() && invalid instanceof Tournament;
        if (shouldRemove) removeTournament((Tournament) invalid);
    }

    public Standings getStandings(Tournament tournament) {
        return ModelUtils.get(tournament, standingsMap, () -> Standings.forTournament(tournament));
    }

    public List<Identifiable> getStatRanks(Tournament tournament) {
        return ModelUtils.get(tournament, ranksMap, ArrayList::new);
    }

    public Completable fetchStandings(Tournament tournament) {
        return api.getStandings(tournament.getId())
                .observeOn(mainThread()).map(getStandings(tournament)::update).toCompletable();
    }

    public Flowable<Boolean> checkForWinner(Tournament tournament) {
        if (tournament.isEmpty()) return Flowable.empty();
        return repository.get(tournament)
                .map(Tournament::hasWinner).observeOn(mainThread());
    }

    public Single<Tournament> delete(final Tournament tournament) {
        return repository.delete(tournament).doOnSuccess(this::removeTournament);
    }

    public Single<DiffUtil.DiffResult> getStatRank(Tournament tournament, StatType type) {
        Single<List<Identifiable>> sourceSingle = api.getStatRanks(tournament.getId(), type).map(ArrayList<Identifiable>::new);
        return Identifiable.diff(sourceSingle, () -> getStatRanks(tournament), ModelUtils::replaceList).observeOn(mainThread());
    }

    private Flowable<Tournament> getTournament(Tournament tournament) {
        return tournament.isEmpty() ? Flowable.empty() : repository.get(tournament);
    }

    private Single<Tournament> createOrUpdateTournament(final Tournament tournament) {
        return repository.createOrUpdate(tournament);
    }

    private void removeTournament(Tournament tournament) {
        for (List<Identifiable> list : modelListMap.values()) list.remove(tournament);
        pushModelAlert(Alert.tournamentDeletion(tournament));
    }
}