# DI implementation on pure Java
Lightweight realisation of Dependency Injection.\
Easy and faster than the well-known framework.

It gives you two annotations:
1. _**@Brick**_ - applicable for classes
2. _**@InsertPlease**_ - applicable for fields

Place _**@Brick**_ over the classes you want instances to be created.\
Boolean option _isMultiple_ is available to create a singleton (default) or prototype `(isMultiple = true)`.\
Classes you want to instantiate must have default constructor.\
Use `void init()` method to run your business logic.\
All classes have to be in a separate ".java" files, no inner classes acceptable.

Use _**@InsertPlease**_ for fields you want to inject instances in.\
If you want to inject an instance of interface and you have more than one implementation
of this interface - use _what_ option to indicate specific class that should be injected.

To start initialization process use static method `go`. It expect a string path to package you want to scan,
staring with "src/main/java/":\
`Summer factory = Summer.go("src/main/java/com/");`

To obtain any singleton instance anywhere you want - call `giveMeInstance` method:\
`Service service = factory.giveMeInstance(Service.class);`

```
    @Brick
    public class Service {
        @InsertPlease
        private Validator validator;
        @InsertPlease(what = DaoImpl.class)
        private Dao dao;

        public Service() {
            System.out.println("in Service constructor");
        }

        public void init() {
            System.out.println("in Service init");
        }
    }

    @Brick(isMultiple = true)
    public class Validator {
        public Validator() {
            System.out.println("in Validator constructor");
        }

        public void init() {
            System.out.println("in Validator init");
        }
    }

    public interface Dao {
    }

    @Brick
    public class InMemoryDao implements Dao {
    }

    @Brick
    public class DaoImpl implements Dao {
        @InsertPlease
        private Validator validator;

        public DaoImpl() {
            System.out.println("in Dao constructor");
        }

        public void init() {
            System.out.println("in Dao init");
        }
    }
```
