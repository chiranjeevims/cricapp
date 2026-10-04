package com.example.domain

import com.example.data.model.DismissalType
import com.example.data.model.ExtraType

/** Who is batting at each end and who is bowling. Null bowler means "pick the next bowler". */
data class Crease(val strikerId: Long?, val nonStrikerId: Long?, val bowlerId: Long?)

data class WicketInput(
    val dismissal: DismissalType,
    /** Defaults to the striker. For run outs it can be the non-striker. */
    val outPlayerId: Long?,
    val fielderId: Long?,
    /** Required unless this wicket ends the innings or the side bats solo. */
    val newBatterId: Long?
)

/**
 * One delivery as entered on the keypad.
 * Wides and no-balls carry their one-run penalty inside [extraRuns]; byes, leg byes and wides
 * include any runs taken. Runs off the bat go in [runsOffBat] (also on a no-ball or run out).
 */
data class DeliveryInput(
    val runsOffBat: Int,
    val extraType: ExtraType,
    val extraRuns: Int,
    val wicket: WicketInput? = null
) {
    val totalRuns: Int get() = runsOffBat + extraRuns
}

object CreaseLogic {

    /**
     * Crease after a delivery:
     *  1. odd runs taken swap the batters;
     *  2. a dismissed batter is replaced in place by the new batter (caught: the new batter faces);
     *  3. end of over swaps ends and asks for a new bowler, unless only one bowler is available.
     * Solo batters never leave the crease and have no partner.
     */
    fun afterDelivery(
        crease: Crease,
        input: DeliveryInput,
        legalBallsBefore: Int,
        ballsPerOver: Int,
        wicketsBefore: Int,
        maxWickets: Int,
        isSoloBatting: Boolean,
        keepBowlerAtOverEnd: Boolean
    ): Crease {
        var striker = crease.strikerId
        var nonStriker = if (isSoloBatting) null else crease.nonStrikerId
        val bpo = ballsPerOver.coerceAtLeast(1)

        val ran = CricketRules.runsRan(input.extraType, input.runsOffBat, input.extraRuns)
        if (!isSoloBatting && nonStriker != null && ran % 2 == 1) {
            val t = striker; striker = nonStriker; nonStriker = t
        }

        val w = input.wicket
        if (w != null && !isSoloBatting) {
            val outId = w.outPlayerId ?: crease.strikerId
            val allOut = wicketsBefore + 1 >= maxWickets
            val incoming = if (allOut) null else w.newBatterId
            when {
                w.dismissal == DismissalType.CAUGHT -> {
                    // Law 18.11: the new batter takes strike, whatever the batters did.
                    if (outId == nonStriker) nonStriker = striker
                    striker = incoming
                }
                outId == striker -> striker = incoming
                outId == nonStriker -> nonStriker = incoming
                else -> striker = incoming
            }
        }

        val legal = CricketRules.isLegalBall(input.extraType)
        val overEnded = legal && (legalBallsBefore + 1) % bpo == 0
        var bowler = crease.bowlerId
        if (overEnded) {
            // Ends change. (If the side was just bowled out there is nobody to move.)
            if (!isSoloBatting && nonStriker != null && striker != null) {
                val t = striker; striker = nonStriker; nonStriker = t
            }
            if (!keepBowlerAtOverEnd) bowler = null
        }
        return Crease(striker, nonStriker, bowler)
    }
}
