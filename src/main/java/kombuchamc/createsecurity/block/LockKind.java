package kombuchamc.createsecurity.block;

public enum LockKind {

    KEYCARD,

    CODE;

    public static LockKind byId(int id) {
        return id == CODE.ordinal() ? CODE : KEYCARD;
    }
}
