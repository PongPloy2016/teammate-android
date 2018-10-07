package com.mainstreetcode.teammate.repository;

import com.mainstreetcode.teammate.model.Role;
import com.mainstreetcode.teammate.model.Team;
import com.mainstreetcode.teammate.model.User;
import com.mainstreetcode.teammate.persistence.AppDatabase;
import com.mainstreetcode.teammate.persistence.EntityDao;
import com.mainstreetcode.teammate.persistence.RoleDao;
import com.mainstreetcode.teammate.rest.TeammateApi;
import com.mainstreetcode.teammate.rest.TeammateService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.functions.Function;
import okhttp3.MultipartBody;

import static io.reactivex.schedulers.Schedulers.io;

public class RoleRepository extends ModelRepository<Role> {

    private final TeammateApi api;
    private final RoleDao roleDao;

    private static RoleRepository ourInstance;

    private RoleRepository() {
        api = TeammateService.getApiInstance();
        roleDao = AppDatabase.getInstance().roleDao();
    }

    public static RoleRepository getInstance() {
        if (ourInstance == null) ourInstance = new RoleRepository();
        return ourInstance;
    }

    @Override
    public EntityDao<? super Role> dao() {
        return roleDao;
    }

    @Override
    public Single<Role> createOrUpdate(Role model) {
        Single<Role> roleSingle = api.updateRole(model.getId(), model)
                .map(getLocalUpdateFunction(model))
                .doOnError(throwable -> deleteInvalidModel(model, throwable));

        MultipartBody.Part body = getBody(model.getHeaderItem().getValue(), Role.PHOTO_UPLOAD_KEY);
        if (body != null) {
            roleSingle = roleSingle.flatMap(put -> api.uploadRolePhoto(model.getId(), body).map(getLocalUpdateFunction(model)));
        }

        return roleSingle.map(getLocalUpdateFunction(model)).map(getSaveFunction());
    }

    @Override
    public Flowable<Role> get(String id) {
        Maybe<Role> local = roleDao.get(id).subscribeOn(io());
        Maybe<Role> remote = api.getRole(id).toMaybe();

        return fetchThenGetModel(local, remote);
    }

    @Override
    public Single<Role> delete(Role model) {
        return api.deleteRole(model.getId())
                .map(this::deleteLocally)
                .doOnError(throwable -> deleteInvalidModel(model, throwable));
    }

    @Override
    Function<List<Role>, List<Role>> provideSaveManyFunction() {
        return models -> {
            int size = models.size();
            List<Team> teams = new ArrayList<>(size);
            List<User> users = new ArrayList<>(size);

            for (int i = 0; i < size; i++) {
                Role role = models.get(i);
                users.add(role.getUser());
                teams.add(role.getTeam());
            }

            if (!teams.isEmpty()) TeamRepository.getInstance().saveAsNested().apply(teams);
            if (!users.isEmpty()) UserRepository.getInstance().saveAsNested().apply(users);

            roleDao.upsert(Collections.unmodifiableList(models));

            return models;
        };
    }

    public Flowable<Role> getRoleInTeam(String userId, String teamId) {
        Function<Role, Flowable<Role>> function = role -> Maybe.concatDelayError(Arrays.asList(Maybe.just(role), api.getRole(role.getId()).toMaybe()));
        return roleDao.getRoleInTeam(userId, teamId).subscribeOn(io())
                .flatMapPublisher(function);
    }

    public Flowable<List<Role>> getMyRoles() {
        String userId = UserRepository.getInstance().getCurrentUser().getId();
        Maybe<List<Role>> local = roleDao.userRoles(userId).subscribeOn(io());
        Maybe<List<Role>> remote = api.getMyRoles().map(getSaveManyFunction()).toMaybe();

        return fetchThenGet(local, remote);
    }
}
