//
/*
Sample:

try {
    if (badCondition == true) {
        return new FResult(null, new BadConditionException("foobar"));
    } else {
        return new FResult(data, null);
    }
} catch (final SomeException ex) {
    return new FResult(null, ex);
}
*/
//

public final class FResult<T> {

    public final T data;
    public final boolean success;
    public final boolean failed;
    public final Throwable error;

    public FResult(final T data, final Throwable error) {
        this.data    = data;
        this.success = error == null;
        this.failed  = !success;
        this.error   = error;
    }
}
