package com.example.firstaid.logic;

import com.example.firstaid.model.RiskLevel;

/**
 * Global in-process coordinator for risk popup/activity display.
 * Holds the highest active or pending risk level to prevent duplicate popups.
 */
public final class RiskPopupCoordinator {

    private static RiskLevel heldRiskLevel = null;
    private static RiskLevel activeRiskLevel = null;

    private RiskPopupCoordinator() {
    }

    public static synchronized boolean tryRequest(RiskLevel requestedLevel) {
        if (!isPopupRisk(requestedLevel)) {
            return false;
        }
        if (heldRiskLevel == null) {
            heldRiskLevel = requestedLevel;
            return true;
        }
        if (requestedLevel.ordinal() > heldRiskLevel.ordinal()) {
            heldRiskLevel = requestedLevel;
            return true;
        }
        return false;
    }

    public static synchronized boolean tryEnter(RiskLevel enteringLevel) {
        if (!isPopupRisk(enteringLevel)) {
            return false;
        }

        // Block duplicate/smaller popup while one is already visible.
        if (activeRiskLevel != null) {
            if (enteringLevel.ordinal() <= activeRiskLevel.ordinal()) {
                return false;
            }
            heldRiskLevel = enteringLevel;
            activeRiskLevel = enteringLevel;
            return true;
        }

        if (heldRiskLevel == null) {
            heldRiskLevel = enteringLevel;
            activeRiskLevel = enteringLevel;
            return true;
        }

        // Consume pending request of the same level.
        if (enteringLevel == heldRiskLevel) {
            activeRiskLevel = enteringLevel;
            return true;
        }

        // Allow entering a higher level than currently held request.
        if (enteringLevel.ordinal() > heldRiskLevel.ordinal()) {
            heldRiskLevel = enteringLevel;
            activeRiskLevel = enteringLevel;
            return true;
        }

        return false;
    }

    public static synchronized void release(RiskLevel leavingLevel) {
        if (leavingLevel == null) {
            return;
        }
        if (activeRiskLevel == leavingLevel) {
            activeRiskLevel = null;
        }
        if (heldRiskLevel == leavingLevel) {
            heldRiskLevel = null;
        }
    }

    private static boolean isPopupRisk(RiskLevel level) {
        return level == RiskLevel.LOW || level == RiskLevel.MEDIUM || level == RiskLevel.HIGH;
    }
}
