package com.github.kayr.jboomerang;

import com.github.kayr.jboomerang.JBoomerang.Propagation;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class JBoomerangTest {

    private int closes     = 0;
    private int exceptions = 0;
    private int work       = 0;
    private int opens      = 0;

    JBoomerang<MyResource> rm;

    @Before
    public void setUp() {
        rm = new JBoomerang<>(new MyFactory());
        closes = 0;
        exceptions = 0;
        work = 0;
        opens = 0;
    }

    private void assertWorkExceptionsCloses(int work, int exceptions, int closes, int opens) {
        assertEquals("Work not equal", work, this.work);
        assertEquals("Exception not equal", exceptions, this.exceptions);
        assertEquals("Close not expected", closes, this.closes);
        assertEquals("Opens not expected", opens, this.opens);
    }


    @Test
    public void withResource1() {
    /*
    work
     */
        rm.withResource(MyResource::work);
        assertWorkExceptionsCloses(1, 0, 1, 1);
        assertEquals(0, rm.countOpenResources());
        assertEquals(0, rm.getDiscriminatorSize());


    }

    @Test
    public void withResource3() {
        /*
        work
        work
        work
         */
        rm.withResource(MyResource::work);
        rm.withResource(MyResource::work);
        rm.withResource(MyResource::work);
        assertWorkExceptionsCloses(3, 0, 3, 3);
        assertEquals(0, rm.countOpenResources());
        assertEquals(0, rm.getDiscriminatorSize());

    }

    @Test
    public void withResourceNested3() {
        /*
        work
            work
            work
                work
         */
        rm.withResource(r1 -> {
            r1.work();
            return rm.withResource(r2 -> {
                r2.work();
                r2.work();
                return rm.withResource(MyResource::work);
            });
        });
        assertWorkExceptionsCloses(4, 0, 1, 1);
        assertEquals(0, rm.countOpenResources());
        assertEquals(0, rm.getDiscriminatorSize());


    }

    @Test
    public void withResourceNestedAndNewPropagation() {
        /*
        work
           new work
                no-work
                    new work
         */
        assertNull(rm.getCurrentResource().orElse(null));

        assertNull(rm.getCurrentResource(JBoomerang.COMMON_DISCRIMINATOR).orElse(null));

        rm.withResource(r1 -> {
            r1.work();

            assertEquals(rm.getCurrentResource().orElse(null),r1);

            rm.consume(Propagation.WITH_NEW, r2 -> {
                r2.work();
                assertEquals(rm.getCurrentResource().orElse(null),r2);

                assertEquals(2, rm.countOpenResources());

                rm.withResource(r3 -> {

                    assertEquals(rm.getCurrentResource().orElse(null),r2);
                    assertEquals(rm.getCurrentResource().orElse(null),r3);


                    assertEquals(2, rm.countOpenResources());

                    return rm.withResource(Propagation.WITH_NEW, r4 -> {

                        assertEquals(rm.getCurrentResource().orElse(null),r4);


                        assertEquals(3, rm.countOpenResources());

                        return r1.work();
                    });
                });

            });

            assertEquals(1, rm.countOpenResources());

            return Void.TYPE;
        });

        assertNull(rm.getCurrentResource().orElse(null));

        assertWorkExceptionsCloses(3, 0, 3, 3);
        assertEquals(0, rm.countOpenResources());
        assertEquals(0, rm.getDiscriminatorSize());


    }

    @Test
    public void withResourceNestedAndNewPropagationAndExceptions() {
        try {
            /*
            work
                new work
                    no-work
                        new-exception
             */
            rm.withResource(r1 -> {
                r1.work();

                rm.withResource(Propagation.WITH_NEW, r2 -> {
                    r2.work();

                    assertEquals(2, rm.countOpenResources());

                    rm.consume(r3 -> {

                        assertEquals(2, rm.countOpenResources());

                        rm.consume(Propagation.WITH_NEW, r4 -> {

                            throw new RuntimeException();

                        });
                    });

                    return Void.TYPE;
                });

                assertEquals(1, rm.countOpenResources());

                return Void.TYPE;
            });
            fail("Did not expect to reach here");
        }
        catch (Exception x) {
            assertWorkExceptionsCloses(2, 3, 3, 3);
        }

        assertEquals(0, rm.countOpenResources());
        assertEquals(0, rm.getDiscriminatorSize());


    }

    @Test
    public void withResourceNestedAndNewTenantsPropagationAndExceptions() {
        Object otherTenant = new Object();

        try {
            /*
            work
                new work
                new tenant
                    no-work
                        new-exception
             */

            rm.withResource(r1 -> {
                r1.work();

                rm.withResource(Propagation.WITH_NEW, r2 -> {
                    r2.work();

                    assertEquals(2, rm.countOpenResources());

                    rm.consume(r3 -> {

                        assertEquals(2, rm.countOpenResources());
                        assertEquals(JBoomerang.COMMON_DISCRIMINATOR,rm.currentDiscriminatorNotNull());

                        //OTHER TENANT
                        rm.withResource(otherTenant, Propagation.JOIN, JBoomerang.Args.none(), r22 -> {

                            assertEquals(otherTenant,rm.currentDiscriminatorNotNull());

                            assertEquals(1, rm.countOpenResources(otherTenant));

                            r22.work();

                            return Void.TYPE;
                        });

                        assertEquals(JBoomerang.COMMON_DISCRIMINATOR,rm.currentDiscriminatorNotNull());


                        rm.consume(Propagation.WITH_NEW, r4 -> {

                            throw new RuntimeException();

                        });
                    });

                    return Void.TYPE;
                });


                assertEquals(1, rm.countOpenResources());

                return Void.TYPE;
            });
            fail("Did not expect to reach here");
        }
        catch (Exception x) {
            assertWorkExceptionsCloses(3, 3, 4, 4);
        }
        assertEquals(0, rm.countOpenResources());
        assertEquals(0, rm.countOpenResources(otherTenant));
        assertEquals(0, rm.getDiscriminatorSize());


    }

    @Test
    public void withResourceNestedAndNewPropagationAndExceptions2() {
        try {
            /*
            work
                new-rsrc
                    work
                      new work
                throw exception
             */
            rm.withResource(r1 -> {
                r1.work();

                rm.consume(Propagation.WITH_NEW, r2 -> {

                    assertEquals(2, rm.countOpenResources());

                    rm.consume(r3 -> {
                        r3.work();
                        assertEquals(2, rm.countOpenResources());

                        rm.consume(Propagation.WITH_NEW, MyResource::work);
                    });

                    throw new RuntimeException();
                });

                assertEquals(1, rm.countOpenResources());

                return Void.TYPE;
            });
            fail("Did not expect to reach here");
        }
        catch (Exception x) {
            assertWorkExceptionsCloses(3, 2, 3, 3);
        }
        assertEquals(0, rm.countOpenResources());


    }

    @Test
    public void withResourceThrowingResourceError() {
        try {
            /*
            work
                new-rsrc
                    work
                      new work
                throw exception
             */
            rm.withResource(r1 -> {
                r1.work();

                rm.consume(Propagation.WITH_NEW, r2 -> {

                    assertEquals(2, rm.countOpenResources());

                    rm.consume(r3 -> {
                        r3.work();
                        assertEquals(2, rm.countOpenResources());

                        rm.consume(Propagation.WITH_NEW, MyResource::work);
                    });

                    r2.makeCloseNoise = true;
                });

                fail("Not expecting to be here");

                return Void.TYPE;
            });
            fail("Did not expect to reach here");
        }
        catch (BoomerangCloseException x) {
            x.printStackTrace();
            assertWorkExceptionsCloses(3, 1, 3, 3);
            assertNull(x.getCause());
        }
        assertEquals(0, rm.countOpenResources());


    }

    @Test
    public void withResourceThrowingResourceErrorAndWorkError() {
        try {
            /*
            work
                new-rsrc
                    work
                      new work
                throw exception
             */
            rm.withResource(r1 -> {
                r1.work();

                rm.consume(Propagation.WITH_NEW, r2 -> {

                    assertEquals(2, rm.countOpenResources());

                    rm.consume(r3 -> {
                        r3.work();
                        assertEquals(2, rm.countOpenResources());

                        rm.consume(Propagation.WITH_NEW, MyResource::work);
                    });

                    r2.makeCloseNoise = true;
                    throw new RuntimeException("my work exception");
                });

                fail("Not expecting to be here");

                return Void.TYPE;
            });
            fail("Did not expect to reach here");
        }
        catch (RuntimeException x) {
            assertWorkExceptionsCloses(3, 2, 3, 3);
            assertNull(x.getCause());
            assertEquals("my work exception", x.getMessage());
            assertEquals("Make Noise", x.getSuppressed()[0].getMessage());
        }
        assertEquals(0, rm.countOpenResources());


    }

    @Test
    public void withResourceThrowingResourceErrorForGenericException() {
        try {
            /*
            work
                new-rsrc
                    work
                      new work
                throw exception
             */
            rm.withResource(r1 -> {
                r1.work();

                rm.consume(Propagation.WITH_NEW, r2 -> {

                    assertEquals(2, rm.countOpenResources());

                    rm.consume(r3 -> {
                        r3.work();
                        assertEquals(2, rm.countOpenResources());

                        rm.consume(Propagation.WITH_NEW, MyResource::work);
                    });

                    r2.makeCloseNoise = true;
                    r2.useGenericException = true;
                });

                fail("Not expecting to be here");

                return Void.TYPE;
            });
            fail("Did not expect to reach here");
        } catch (BoomerangCloseException x) {
            Throwable cause = x.getCause();
            assertNotNull(cause);
            assertFalse(cause instanceof BoomerangCloseException);
            assertEquals("Make Noise generic", cause.getMessage());
            assertWorkExceptionsCloses(3, 1, 3, 3);
        }
        assertEquals(0, rm.countOpenResources());


    }

    @Test
    public void testCurrentDiscriminatorReturnsCurrentResource() {
        rm.consume(r1 -> rm.consume(r2 -> {
            assertEquals(1, rm.countOpenResources());
            assertEquals(1, rm.countDiscriminators());
        }));

        assertEquals(0, rm.countDiscriminators());


        rm.consume(r1 -> rm.consume(r2 -> {
            assertEquals(1, rm.countOpenResources());
            rm.consume(Propagation.WITH_NEW, r3 -> {
                assertEquals(2, rm.countOpenResources());
                assertEquals(JBoomerang.COMMON_DISCRIMINATOR, rm.currentDiscriminatorNotNull());
                assertEquals(1, rm.countDiscriminators());

                rm.consume("newDiscriminator", Propagation.JOIN, r11 -> {
                    assertEquals(1, rm.countOpenResources());
                    assertEquals("newDiscriminator", rm.currentDiscriminatorNotNull());
                });
                assertEquals(2, rm.countOpenResources());
                assertEquals(JBoomerang.COMMON_DISCRIMINATOR, rm.currentDiscriminatorNotNull());
            });
            assertEquals(2, rm.countDiscriminators());
        }));

        assertEquals(0, rm.countDiscriminators());
    }



    public class MyResource {

        MyResource() {
            ++opens;
        }

        int work() {
            return ++work;
        }

        public void close() {
            closes++;
            if (makeCloseNoise) {
                if (useGenericException) {
                    ExceptionUtil.sneakyThrow(new Exception("Make Noise generic"));
                } else {
                    throw new BoomerangCloseException("Make Noise");
                }
            }
        }

        void onException() {
            exceptions++;
        }

        boolean makeCloseNoise = false;
        boolean useGenericException = false;
    }


    @Test
    public void testRequiredPropagation() {
        try {
            rm.consume(Propagation.REQUIRED, r -> {
                fail("not expecting to be here");
            });
        } catch (IllegalStateException e) {
            assertEquals("a resource is required ot be available at this time however non is available", e.getMessage());
        }

        rm.consume(Propagation.JOIN, r -> {
            assertEquals(1L, rm.countOpenResources());
            rm.consume(Propagation.REQUIRED, r2 -> assertEquals(1L, rm.countOpenResources()));
        });
    }

    @Test
    public void testNONE_Propagation() {

        rm.consume(Propagation.JOIN, r -> {
            assertEquals(1L, rm.countOpenResources());
            r.work();
            try {
                rm.consume(Propagation.NONE, r2 -> Assert.fail("not expected here"));
            } catch (IllegalStateException x) {
                final String message = x.getMessage();
                Assert.assertTrue(message.contains("cannot executed function with an open resource"));
            }
        });
        assertWorkExceptionsCloses(1, 0, 1, 1);
    }

    class MyFactory implements JBoomerang.ResourceFactory<MyResource> {

        @Override
        public MyResource create(Object discrimator, JBoomerang.Args args) {
            return new MyResource();
        }

        @Override
        public void close(Object discriminator, MyResource resource) {
            resource.close();
        }

        @Override
        public void onException(Object discriminator, MyResource resource) {
            resource.onException();
        }
    }
}