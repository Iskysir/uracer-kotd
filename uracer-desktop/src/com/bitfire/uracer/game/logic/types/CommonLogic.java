
package com.bitfire.uracer.game.logic.types;

import aurelienribon.tweenengine.Tween;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.bitfire.uracer.Input;
import com.bitfire.uracer.ScalingStrategy;
import com.bitfire.uracer.URacer;
import com.bitfire.uracer.configuration.Config;
import com.bitfire.uracer.configuration.Gameplay;
import com.bitfire.uracer.configuration.Gameplay.TimeDilateInputMode;
import com.bitfire.uracer.configuration.UserPreferences;
import com.bitfire.uracer.configuration.UserPreferences.Preference;
import com.bitfire.uracer.game.DebugHelper;
import com.bitfire.uracer.game.GameLogic;
import com.bitfire.uracer.game.GameplaySettings;
import com.bitfire.uracer.game.actors.Car;
import com.bitfire.uracer.game.actors.CarEvent;
import com.bitfire.uracer.game.actors.CarPreset;
import com.bitfire.uracer.game.actors.CarState;
import com.bitfire.uracer.game.actors.CarStateEvent;
import com.bitfire.uracer.game.actors.GhostCar;
import com.bitfire.uracer.game.logic.gametasks.GameTasksManager;
import com.bitfire.uracer.game.logic.gametasks.hud.HudLabel;
import com.bitfire.uracer.game.logic.gametasks.hud.HudLabelAccessor;
import com.bitfire.uracer.game.logic.gametasks.hud.elements.HudPlayerDriftInfo.EndDriftType;
import com.bitfire.uracer.game.logic.gametasks.messager.Message;
import com.bitfire.uracer.game.logic.gametasks.messager.Message.Position;
import com.bitfire.uracer.game.logic.gametasks.messager.Message.Size;
import com.bitfire.uracer.game.logic.gametasks.messager.Message.Type;
import com.bitfire.uracer.game.logic.gametasks.messager.MessageAccessor;
import com.bitfire.uracer.game.logic.helpers.CarFactory;
import com.bitfire.uracer.game.logic.helpers.PlayerGameTasks;
import com.bitfire.uracer.game.logic.post.PostProcessing;
import com.bitfire.uracer.game.logic.post.animators.AggressiveCold;
import com.bitfire.uracer.game.logic.replaying.LapManager;
import com.bitfire.uracer.game.logic.replaying.Replay;
import com.bitfire.uracer.game.logic.types.common.TimeModulator;
import com.bitfire.uracer.game.player.PlayerCar;
import com.bitfire.uracer.game.player.PlayerDriftStateEvent;
import com.bitfire.uracer.game.rendering.GameRenderer;
import com.bitfire.uracer.game.rendering.GameWorldRenderer;
import com.bitfire.uracer.game.screens.GameScreensFactory.ScreenType;
import com.bitfire.uracer.game.tween.GameTweener;
import com.bitfire.uracer.game.tween.SysTweener;
import com.bitfire.uracer.game.world.GameWorld;
import com.bitfire.uracer.screen.TransitionFactory.TransitionType;
import com.bitfire.uracer.utils.BoxedFloat;
import com.bitfire.uracer.utils.BoxedFloatAccessor;
import com.bitfire.uracer.utils.CarUtils;
import com.bitfire.uracer.utils.NumberString;

public abstract class CommonLogic implements GameLogic, CarEvent.Listener, CarStateEvent.Listener, PlayerDriftStateEvent.Listener {
	// input
	protected Input input = null;

	// world
	protected GameWorld gameWorld = null;

	// rendering
	// private GameRenderer gameRenderer = null;
	protected GameWorldRenderer gameWorldRenderer = null;
	protected PostProcessing postProcessing = null;

	// player
	protected PlayerCar playerCar = null;
	protected GhostCar ghostCar = null;

	// lap
	protected LapManager lapManager = null;
	protected boolean isFirstLap = true;

	// tasks
	protected GameTasksManager gameTasksManager = null;
	protected PlayerGameTasks playerTasks = null;

	// time modulation logic
	private boolean timeModulation;
	private TimeModulator timeMod = null;
	private TimeDilateInputMode timeDilateMode;

	public CommonLogic (GameWorld gameWorld, GameRenderer gameRenderer, ScalingStrategy scalingStrategy) {
		this.gameWorld = gameWorld;
		// this.gameRenderer = gameRenderer;
		this.gameWorldRenderer = gameRenderer.getWorldRenderer();
		this.input = URacer.Game.getInputSystem();
		timeDilateMode = Gameplay.TimeDilateInputMode.valueOf(UserPreferences.string(Preference.TimeDilateInputMode));
		timeMod = new TimeModulator();

		// create tweening support
		Tween.registerAccessor(Message.class, new MessageAccessor());
		Tween.registerAccessor(HudLabel.class, new HudLabelAccessor());
		Tween.registerAccessor(BoxedFloat.class, new BoxedFloatAccessor());
		Gdx.app.log("GameLogic", "Tweening helpers created");

		// post-processing
		postProcessing = new PostProcessing(gameRenderer.getPostProcessor());

		if (gameRenderer.hasPostProcessor()) {
			postProcessing.addAnimator(AggressiveCold.Name, new AggressiveCold(this, postProcessing, gameWorld.isNightMode()));
			postProcessing.enableAnimator(AggressiveCold.Name);
		}

		Gdx.app.log("GameLogic", "Post-processing animator created");

		// main game tasks
		gameTasksManager = new GameTasksManager(gameWorld, scalingStrategy);
		gameTasksManager.createTasks();

		// player tasks
		playerTasks = new PlayerGameTasks(gameTasksManager, scalingStrategy);

		lapManager = new LapManager(gameWorld.trackName);
		ghostCar = CarFactory.createGhost(gameWorld, CarPreset.Type.L1_GoblinOrange);

		// messager.show( "COOL STUFF!", 60, Message.Type.Information,
		// MessagePosition.Bottom, MessageSize.Big );
	}

	@Override
	public void dispose () {
		gameTasksManager.dispose();
		playerTasks.dispose();

		if (playerCar != null) {
			playerCar.dispose();
		}

		if (ghostCar != null) {
			ghostCar.dispose();
		}

		GameTweener.dispose();
	}

	//
	// specific game logic shall also implement these
	//

	protected abstract void updateCamera (float timeModFactor);

	protected abstract void restart ();

	protected abstract void reset ();

	protected abstract void newReplay ();

	protected abstract void driftBegins ();

	protected abstract void driftEnds ();

	protected abstract void timeDilationBegins ();

	protected abstract void timeDilationEnds ();

	//
	// SHARED OPERATIONS (Subclass Sandbox pattern)
	//

	/** Restarts the current game */
	protected void restartGame () {
		restartLogic();
// gameTasksManager.restart();
		restart();
	}

	/** Restart and completely resets the game, removing any playing replay so far */
	protected void resetGame () {
		restartLogic();
		resetLogic();
// gameTasksManager.reset();
		reset();
	}

	/** Sets the player from the specified preset */
	@Override
	public void setPlayer (CarPreset.Type presetType) {
		if (hasPlayer()) {
			Gdx.app.log("GameLogic", "A player already exists.");
			return;
		}

		playerCar = CarFactory.createPlayer(gameWorld, presetType);

		configurePlayer(gameWorld, input /* gameTasksManager.input */, playerCar);
		Gdx.app.log("GameLogic", "Player configured");

		playerTasks.createTasks(playerCar, lapManager.getLapInfo());
		Gdx.app.log("GameLogic", "Game tasks created and configured");

		registerPlayerEvents(playerCar);
		Gdx.app.log("GameLogic", "Registered player-related events");

		gameWorldRenderer.setRenderPlayerHeadlights(gameWorld.isNightMode());

		restartGame();

		if (Config.Debug.UseDebugHelper) {
			DebugHelper.setPlayer(playerCar);
		}
	}

	protected void removePlayer () {
		if (!hasPlayer()) {
			Gdx.app.log("GameLogic", "There is no player to remove.");
			return;
		}

		// setting a null player (disabling player), unregister
		// previously registered events, if there was a player
		if (playerCar != null) {
			unregisterPlayerEvents(playerCar);
			playerTasks.destroyTasks();
			playerCar.dispose();
		}

		playerCar = null;

		gameWorldRenderer.setRenderPlayerHeadlights(false);

		if (Config.Debug.UseDebugHelper) {
			DebugHelper.setPlayer(null);
		}
	}

	public GameWorld getGameWorld () {
		return gameWorld;
	}

	public boolean hasPlayer () {
		return playerCar != null;
	}

	public PlayerCar getPlayer () {
		return playerCar;
	}

	//
	// private implementation
	//

	private void registerPlayerEvents (PlayerCar player) {
		player.carState.event.addListener(this, CarStateEvent.Type.onTileChanged);
		player.driftState.event.addListener(this, PlayerDriftStateEvent.Type.onBeginDrift);
		player.driftState.event.addListener(this, PlayerDriftStateEvent.Type.onEndDrift);
		player.event.addListener(this, CarEvent.Type.onCollision);
		player.event.addListener(this, CarEvent.Type.onComputeForces);
	}

	private void unregisterPlayerEvents (PlayerCar player) {
		player.carState.event.removeListener(this, CarStateEvent.Type.onTileChanged);
		player.driftState.event.removeListener(this, PlayerDriftStateEvent.Type.onBeginDrift);
		player.driftState.event.removeListener(this, PlayerDriftStateEvent.Type.onEndDrift);
		player.event.removeListener(this, CarEvent.Type.onCollision);
		player.event.removeListener(this, CarEvent.Type.onComputeForces);
	}

	private void configurePlayer (GameWorld world, Input inputSystem, PlayerCar player) {
		// create player and setup player input system and initial position in
		// the world
		playerCar.setInputSystem(inputSystem);
		player.setWorldPosMt(world.playerStartPos, world.playerStartOrient);
		player.resetPhysics();
	}

	private void resetPlayer (Car playerCar, GhostCar playerGhostCar) {
		if (playerCar != null) {
			playerCar.resetPhysics();
			playerCar.resetDistanceAndSpeed();
			playerCar.setWorldPosMt(gameWorld.playerStartPos, gameWorld.playerStartOrient);
		}

		if (playerGhostCar != null) {
			playerGhostCar.resetPhysics();
			playerGhostCar.resetDistanceAndSpeed();
			playerGhostCar.removeReplay();
		}
	}

	private void restartLogic () {
		resetPlayer(playerCar, ghostCar);
		gameWorldRenderer.setInitialCameraPositionOrient(playerCar);

		isFirstLap = true;
		timeModulation = false;
		timeMod.reset();
		SysTweener.clear();
		GameTweener.clear();
		lapManager.abortRecording();
		gameTasksManager.restart();
	}

	private void resetLogic () {
		lapManager.abortRecording();
		lapManager.reset();
		gameTasksManager.reset();
	}

	//
	// implement interfaces and listeners callbacks
	//

	@Override
	public void tick () {
		processInput();
	}

	@Override
	public void tickCompleted () {
		gameTasksManager.physicsStep.onSubstepCompleted();
	}

	@Override
	public void beforeRender () {
		URacer.timeMultiplier = timeMod.getTime();
		float timeModFactor = 1 - (URacer.timeMultiplier - TimeModulator.MinTime) / (TimeModulator.MaxTime - TimeModulator.MinTime);

		updateCamera(timeModFactor);

		// post-processing step
		postProcessing.onBeforeRender(timeModFactor);

		// camera update
		gameWorldRenderer.updateCamera();

		// game tweener step
		GameTweener.update();
	}

	private Replay userRec = null;

	private void processInput () {
		// fast car switch (debug!)
		for (int i = Keys.NUM_1; i <= Keys.NUM_9; i++) {
			if (input.isPressed(i)) {
				CarPreset.Type type = CarPreset.Type.values()[i - Keys.NUM_1];
				removePlayer();
				setPlayer(type);
			}
		}

		if (input.isPressed(Keys.C)) {

			if (lapManager.getBestReplay() != null) {
				ghostCar.setReplay(lapManager.getBestReplay());
			}

		} else if (input.isPressed(Keys.R)) {

			// restart

			restartGame();

		} else if (input.isPressed(Keys.T)) {

			// reset

			resetGame();

		} else if (input.isPressed(Keys.Z)) {

			// FIXME this should go in some sort of DebugLogic thing..

			// start recording
			playerCar.resetDistanceAndSpeed();
			ghostCar.setReplay(null);
			lapManager.abortRecording();
			userRec = lapManager.startRecording(playerCar);
			Gdx.app.log("GameLogic", "Recording...");

		} else if (input.isPressed(Keys.X)) {

			// FIXME this should go in some sort of DebugLogic thing..

			// stop recording and play
			playerCar.resetPhysics();
			lapManager.stopRecording();

			CarUtils.dumpSpeedInfo("Player", playerCar, lapManager.getLastRecordedReplay().trackTimeSeconds);
			playerCar.resetDistanceAndSpeed();
			if (userRec != null) {
				userRec.saveLocal(gameTasksManager.messager);
				ghostCar.setReplay(userRec);
			}

			// Gdx.app.log( "GameLogic", "Player final pos=" +
			// playerCar.getBody().getPosition() );

		} else if (input.isPressed(Keys.Q) || input.isPressed(Keys.ESCAPE) || input.isPressed(Keys.BACK)) {

			// quit
			URacer.Screens.setScreen(ScreenType.MainScreen, TransitionType.Fader, 500);
			// URacer.Screens.setScreen( ScreenType.ExitScreen, TransitionType.Fader, 500 );

		} else if (input.isPressed(Keys.O)) {

			// FIXME this should go in some sort of DebugLogic thing..

			// remove player

			removePlayer();

		} else if (input.isPressed(Keys.P)) {

			// FIXME this should go in some sort of DebugLogic thing..

			// add player

			setPlayer(CarPreset.Type.L1_GoblinOrange);

		} else if (input.isPressed(Keys.W)) {

			// FIXME this should go in some sort of DebugLogic thing..
			Config.Debug.RenderBox2DWorldWireframe = !Config.Debug.RenderBox2DWorldWireframe;

		} else if (input.isPressed(Keys.B)) {

			// FIXME this should go in some sort of DebugLogic thing..
			Config.Debug.Render3DBoundingBoxes = !Config.Debug.Render3DBoundingBoxes;

		}

		switch (timeDilateMode) {
		case Toggle:
			if (input.isPressed(Keys.SPACE) || input.isTouched(1)) {
				timeModulation = !timeModulation;

				if (timeModulation) {
					timeMod.toDilatedTime();
					timeDilationBegins();
				} else {
					timeMod.toNormalTime();
					timeDilationEnds();
				}
			}
			break;

		case TouchAndRelease:

			if (input.isPressed(Keys.SPACE) || input.isTouched(1)) {
				if (!timeModulation) {
					timeModulation = true;
					timeMod.toDilatedTime();
					timeDilationBegins();
				}
			} else if (input.isReleased(Keys.SPACE) || input.isUntouched(1)) {
				if (timeModulation) {
					timeModulation = false;
					timeMod.toNormalTime();
					timeDilationEnds();
				}
			}
			break;
		}
	}

	//
	// EVENT HANDLERS
	//

	@Override
	public void carEvent (CarEvent.Type type, CarEvent.Data data) {
		switch (type) {
		case onCollision:

			// invalidate drifting
			if (playerCar.driftState.isDrifting) {
				playerCar.driftState.invalidateByCollision();
			}

			// invalidate time modulation
			if (timeModulation) {
				timeModulation = false;
				timeMod.toNormalTime();
			}

			break;
		case onComputeForces:
			lapManager.record(data.forces);
		}
	}

	@Override
	public void carStateEvent (CarState source, CarStateEvent.Type type) {
		switch (type) {
		case onTileChanged:
			playerTileChanged();
			break;
		}
	}

	// NOTE: no events for the GhostCar since we record the forces generated by the CarSimulator
	@Override
	public void playerDriftStateEvent (PlayerCar player, PlayerDriftStateEvent.Type type) {
		switch (type) {
		case onBeginDrift:
			playerTasks.hudPlayerDriftInfo.beginDrift();
			driftBegins();
			break;
		case onEndDrift:
			driftEnds();

			float driftSeconds = player.driftState.driftSeconds();
			String msgSeconds = NumberString.format(playerCar.driftState.driftSeconds()) + "  seconds!";

			if (player.driftState.hasCollided) {
				playerTasks.hudPlayerDriftInfo.endDrift("-" + NumberString.format(driftSeconds), EndDriftType.BadDrift);
			} else {

				if (driftSeconds >= 1 && driftSeconds < 3f) {
					gameTasksManager.messager.enqueue("NICE ONE!\n+" + msgSeconds, 1f, Type.Good, Position.Bottom, Size.Big);
				} else if (driftSeconds >= 3f && driftSeconds < 5f) {
					gameTasksManager.messager.enqueue("FANTASTIC!\n+" + msgSeconds, 1f, Type.Good, Position.Bottom, Size.Big);
				} else if (driftSeconds >= 5f) {
					gameTasksManager.messager.enqueue("UNREAL!\n+" + msgSeconds, 1f, Type.Good, Position.Bottom, Size.Big);
				}

				playerTasks.hudPlayerDriftInfo.endDrift("+" + NumberString.format(driftSeconds), EndDriftType.GoodDrift);
			}

			break;
		}
	}

	// FIXME: the method used to detect the player completing a lap can be more decent than this
	// and also perform "wrong way" checks and such
	private void playerTileChanged () {
		boolean onStartZone = (playerCar.carState.currTileX == gameWorld.playerStartTileX && playerCar.carState.currTileY == gameWorld.playerStartTileY);

		if (onStartZone) {
			if (isFirstLap) {
				isFirstLap = false;

				lapManager.startRecording(playerCar);

			} else {

				// detect and ignore invalid laps
				if (lapManager.getLapInfo().getElapsedSeconds() < GameplaySettings.ReplayMinDurationSecs) {
					Gdx.app.log("CommonLogic", "Invalid lap detected, too short (" + lapManager.getLapInfo().getElapsedSeconds()
						+ "sec < " + GameplaySettings.ReplayMinDurationSecs + ")");
					return;
				}

				lapManager.stopRecording();
				newReplay();
				lapManager.startRecording(playerCar);
			}

			playerCar.resetDistanceAndSpeed();
		}
	}
}
