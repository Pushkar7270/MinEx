package com.minex.backend.security;

import com.minex.backend.config.AppProps;
import com.minex.backend.service.CurrentUserService;
import org.springframework.stereotype.Component;

/**
 * The single place where a role's privileges are defined.
 *
 * <p>Deliberately keyed off the role's <b>numeric rank</b> (data stored on the
 * {@code roles} table) rather than role names, and off configurable thresholds
 * ({@code app.rbac.*}). When the real role taxonomy arrives, insert/update the
 * rows and tune the thresholds — no code has to name any role.
 *
 * <p>Usable from method security as {@code @PreAuthorize("@rbac.canReview()")}.
 */
@Component("rbac")
public class Rbac {
    private final AppProps props;
    private final CurrentUserService currentUser;

    public Rbac(AppProps props, CurrentUserService currentUser) {
        this.props = props;
        this.currentUser = currentUser;
    }

    /** May submit corrections / upload documents. */
    public boolean canCorrect() {
        return rank() >= props.getRbac().getCorrectRank();
    }

    /** May approve or reject submitted figures. */
    public boolean canReview() {
        return rank() >= props.getRbac().getReviewRank();
    }

    /** May publish approved figures to dashboards. */
    public boolean canPublish() {
        return rank() >= props.getRbac().getPublishRank();
    }

    /** May change other users' roles. */
    public boolean canManageUsers() {
        return rank() >= props.getRbac().getAdminRank();
    }

    public int rank() {
        return currentUser.requireCurrentUser().getRole().getRank();
    }
}
