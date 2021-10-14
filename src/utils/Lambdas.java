/*

Very useful for creating something similar to 'local functions'.

Example:

void someFunction() {

    final Lambdas.Binary<Integer, Integer, Integer> f = (a, b) -> {
        return a + b;
    };
    final Integer result = f.call(3, 5);
    System.out.println(result);

    final Lambdas.Nullary<Void> f2 = () -> {
        for (int i = 0; i < 100; ++i) {
            System.out.println(i);
        }
        return (Void) null;
    };
    f2.call();
}

*/
public final class Lambdas {

    private Lambdas() {
        assert false;
    }

    @FunctionalInterface
    public interface Nullary<Result> {

        public Result call();
    }

    @FunctionalInterface
    public interface Unary<Result, Param1> {

         public Result call(final Param1 p1);
    }

    @FunctionalInterface
    public interface Binary<Result, Param1, Param2> {

        public Result call(final Param1 p1, final Param2 p2);
    }

    @FunctionalInterface
    public interface Ternary<Result, Param1, Param2, Param3> {

        public Result call(final Param1 p1, final Param2 p2, final Param3 p3);
    }

    @FunctionalInterface
    public interface Quaternary<Result, Param1, Param2, Param3, Param4> {
        public Result call(final Param1 p1, final Param2 p2, final Param3 p3,
                           final Param4 p4);
    }

    @FunctionalInterface
    public interface Quinary<Result, Param1, Param2, Param3, Param4, Param5> {

        public Result call(final Param1 p1, final Param2 p2, final Param3 p3,
                           final Param4 p4, final Param5 p5);
    }

    @FunctionalInterface
    public interface Senary<Result, Param1, Param2, Param3, Param4, Param5,
                            Param6> {

        public Result call(final Param1 p1, final Param2 p2, final Param3 p3,
                           final Param4 p4, final Param5 p5, final Param6 p6);
    }

    @FunctionalInterface
    public interface Septenary<Result, Param1, Param2, Param3, Param4, Param5,
                               Param6, Param7> {

        public Result call(final Param1 p1, final Param2 p2, final Param3 p3,
                           final Param4 p4, final Param5 p5, final Param6 p6,
                           final Param7 p7);
    }

    @FunctionalInterface
    public interface Octonary<Result, Param1, Param2, Param3, Param4, Param5,
                              Param6, Param7, Param8> {

        public Result call(final Param1 p1, final Param2 p2, final Param3 p3,
                           final Param4 p4, final Param5 p5, final Param6 p6,
                           final Param7 p7, final Param8 p8);
    }

    @FunctionalInterface
    public interface Novenary<Result, Param1, Param2, Param3, Param4, Param5,
                              Param6, Param7, Param8, Param9> {

        public Result call(final Param1 p1, final Param2 p2, final Param3 p3,
                           final Param4 p4, final Param5 p5, final Param6 p6,
                           final Param7 p7, final Param8 p8, final Param9 p9);
    }

    @FunctionalInterface
    public interface Denary<Result, Param1, Param2, Param3, Param4, Param5,
                            Param6, Param7, Param8, Param9, Param10> {

        public Result call(final Param1 p1, final Param2 p2, final Param3 p3,
                           final Param4 p4, final Param5 p5, final Param6 p6,
                           final Param7 p7, final Param8 p8, final Param9 p9,
                           final Param10 p10);
    }

    @FunctionalInterface
    public interface NullaryEx<Result> {

        public Result call() throws Exception;
    }

    @FunctionalInterface
    public interface UnaryEx<Result, Param1> {

         public Result call(final Param1 p1) throws Exception;
    }

    @FunctionalInterface
    public interface BinaryEx<Result, Param1, Param2> {

        public Result call(final Param1 p1, final Param2 p2) throws Exception;
    }

    @FunctionalInterface
    public interface TernaryEx<Result, Param1, Param2, Param3> {

        public Result call(final Param1 p1, final Param2 p2, final Param3 p3)
                           throws Exception;
    }

    @FunctionalInterface
    public interface QuaternaryEx<Result, Param1, Param2, Param3, Param4> {
        public Result call(final Param1 p1, final Param2 p2, final Param3 p3,
                           final Param4 p4) throws Exception;
    }

    @FunctionalInterface
    public interface QuinaryEx<Result, Param1, Param2, Param3, Param4,
                               Param5> {

        public Result call(final Param1 p1, final Param2 p2, final Param3 p3,
                           final Param4 p4, final Param5 p5) throws Exception;
    }

    @FunctionalInterface
    public interface SenaryEx<Result, Param1, Param2, Param3, Param4, Param5,
                            Param6> {

        public Result callEx(final Param1 p1, final Param2 p2, final Param3 p3,
                           final Param4 p4, final Param5 p5, final Param6 p6)
                           throws Exception;
    }

    @FunctionalInterface
    public interface SeptenaryEx<Result, Param1, Param2, Param3, Param4,
                               Param5, Param6, Param7> {

        public Result call(final Param1 p1, final Param2 p2, final Param3 p3,
                           final Param4 p4, final Param5 p5, final Param6 p6,
                           final Param7 p7) throws Exception;
    }

    @FunctionalInterface
    public interface OctonaryEx<Result, Param1, Param2, Param3, Param4, Param5,
                              Param6, Param7, Param8> {

        public Result call(final Param1 p1, final Param2 p2, final Param3 p3,
                           final Param4 p4, final Param5 p5, final Param6 p6,
                           final Param7 p7, final Param8 p8) throws Exception;
    }

    @FunctionalInterface
    public interface NovenaryEx<Result, Param1, Param2, Param3, Param4, Param5,
                              Param6, Param7, Param8, Param9> {

        public Result call(final Param1 p1, final Param2 p2, final Param3 p3,
                           final Param4 p4, final Param5 p5, final Param6 p6,
                           final Param7 p7, final Param8 p8, final Param9 p9)
                           throws Exception;
    }

    @FunctionalInterface
    public interface DenaryEx<Result, Param1, Param2, Param3, Param4, Param5,
                            Param6, Param7, Param8, Param9, Param10> {

        public Result call(final Param1 p1, final Param2 p2, final Param3 p3,
                           final Param4 p4, final Param5 p5, final Param6 p6,
                           final Param7 p7, final Param8 p8, final Param9 p9,
                           final Param10 p10) throws Exception;
    }
}
