
package com.bitfire.uracer.game.logic.types;

import com.badlogic.gdx.Gdx;
import com.bitfire.uracer.ScalingStrategy;
import com.bitfire.uracer.game.logic.gametasks.messager.Message.Position;
import com.bitfire.uracer.game.logic.gametasks.messager.Message.Size;
import com.bitfire.uracer.game.logic.gametasks.messager.Message.Type;
import com.bitfire.uracer.game.logic.gametasks.messager.Messager;
import com.bitfire.uracer.game.logic.replaying.Replay;
import com.bitfire.uracer.game.rendering.GameRenderer;
import com.bitfire.uracer.game.rendering.GameWorldRenderer;
import com.bitfire.uracer.game.world.GameWorld;
import com.bitfire.uracer.utils.AMath;
import com.bitfire.uracer.utils.CarUtils;
import com.bitfire.uracer.utils.NumberString;

public class SinglePlayerLogic extends CommonLogic {

	private Messager messager;

	public SinglePlayerLogic (GameWorld gameWorld, GameRenderer gameRenderer, ScalingStrategy scalingStrategy) {
		super(gameWorld, gameRenderer, scalingStrategy);

		messager = gameTasksManager.messager;
	}

	//
	// utilities
	//
	private void setBestLocalReplay () {
		Replay replay = Replay.loadLocal(gameWorld.trackName);
		if (replay == null) {
			return;
		}

		lapManager.setAsBestReplay(replay);
		ghostCar.setReplay(replay);
	}

	//
	// event listeners / callbacks
	//

	// the camera needs to be positioned
	@Override
	protected void updateCamera (float timeModFactor) {
		gameWorldRenderer.setCameraZoom(1.0f + (GameWorldRenderer.MaxCameraZoom - 1) * timeModFactor);

		// update player's headlights and move the world camera to follows it, if there is a player
		if (hasPlayer()) {

			if (gameWorld.isNightMode()) {
				gameWorldRenderer.updatePlayerHeadlights(playerCar);
			}

			gameWorldRenderer.setCameraPosition(playerCar.state().position, playerCar.state().orientation,
				playerCar.carState.currSpeedFactor);

		} else if (ghostCar.hasReplay()) {

			gameWorldRenderer.setCameraPosition(ghostCar.state().position, ghostCar.state().orientation, 0);

		} else {

			// no ghost, no player, WTF?
			gameWorldRenderer.setCameraPosition(gameWorld.playerStartPos, gameWorld.playerStartOrient, 0);
		}
	}

	// the game has been restarted
	@Override
	protected void restart () {
		Gdx.app.log("SinglePlayerLogic", "Starting/restarting game");
		setBestLocalReplay();
	}

	// the game has been reset
	@Override
	protected void reset () {
		Gdx.app.log("SinglePlayerLogic", "Resetting game");
	}

	// a freshly-recorded Replay from the player is available
	@Override
	public void newReplay () {
		Replay replay = lapManager.getLastRecordedReplay();

		// choose wich replay(s) to start playing
		if (!lapManager.hasAllReplays()) {
			// only one replay

			ghostCar.setReplay(replay);
			replay.saveLocal(messager);
			messager.show("GO!  GO!  GO!", 3f, Type.Information, Position.Middle, Size.Big);

		} else {

			// both valid, replay best, overwrite worst

			Replay best = lapManager.getBestReplay();
			Replay worst = lapManager.getWorstReplay();

			float bestTime = AMath.round(best.trackTimeSeconds, 2);
			float worstTime = AMath.round(worst.trackTimeSeconds, 2);
			float diffTime = AMath.round(worstTime - bestTime, 2);

			if (AMath.equals(worstTime, bestTime)) {
				// draw!
				messager.show("DRAW!", 3f, Type.Information, Position.Bottom, Size.Big);
			} else {
				// has the player managed to beat the best lap?
				if (lapManager.isLastBestLap()) {
					messager.show("-" + NumberString.format(diffTime) + " seconds!", 3f, Type.Good, Position.Bottom, Size.Big);
				} else {
					messager.show("+" + NumberString.format(diffTime) + " seconds", 3f, Type.Bad, Position.Bottom, Size.Big);
				}
			}

			ghostCar.setReplay(best);
			best.saveLocal(messager);
		}

		CarUtils.dumpSpeedInfo("Player", playerCar, replay.trackTimeSeconds);
	}

	// the player begins drifting
	@Override
	public void driftBegins () {
	}

	// the player's drift ended
	@Override
	public void driftEnds () {
		Gdx.app.log("SinglePlayerLogic", "drifted for " + playerCar.driftState.driftSeconds() + "s");
	}

	// the player begins slowing down time
	@Override
	public void timeDilationBegins () {
	}

	// the player ends slowing down time
	@Override
	public void timeDilationEnds () {
	}
}
