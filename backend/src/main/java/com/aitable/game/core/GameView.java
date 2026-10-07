package com.aitable.game.core;

/**
 * 下发给<b>某一个玩家</b>的完整视图 = 公共状态 + 他自己的私人状态。
 *
 * <p>前端拿到的永远是它，而不是引擎内部的完整状态。</p>
 */
public class GameView {

    private PublicGameState publicState;

    private PlayerPrivateState privateState;

    public static GameView of(PublicGameState publicState, PlayerPrivateState privateState) {
        GameView view = new GameView();
        view.publicState = publicState;
        view.privateState = privateState;
        return view;
    }

    public PublicGameState getPublicState() {
        return publicState;
    }

    public void setPublicState(PublicGameState publicState) {
        this.publicState = publicState;
    }

    public PlayerPrivateState getPrivateState() {
        return privateState;
    }

    public void setPrivateState(PlayerPrivateState privateState) {
        this.privateState = privateState;
    }
}
