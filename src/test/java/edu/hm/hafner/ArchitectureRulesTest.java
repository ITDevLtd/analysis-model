package edu.hm.hafner;

import javax.xml.parsers.SAXParser;

import org.apache.commons.digester3.Digester;
import org.apache.commons.digester3.binder.DigesterLoader;
import org.junit.jupiter.api.Test;
import org.xml.sax.XMLReader;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.*;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import edu.hm.hafner.util.VisibleForTesting;

/**
 * Defines several architecture rules for the static analysis model and parsers.
 *
 * @author Ullrich Hafner
 */
public class ArchitectureRulesTest {
    private static final DescribedPredicate<JavaCall<?>> ACCESS_IS_RESTRICTED_TO_TESTS = new AccessRestrictedToTests();

    /**
     * Returns the classes that should be checked. Override this method in modules that depend on the analysis-model
     * library.
     *
     * @return the java classes to check
     */
    protected JavaClasses getAllClasses() {
        return new ClassFileImporter().importPackages("edu.hm.hafner");
    }

    /**
     * Digester must not be used directly, rather use a SecureDigester instance.
     */
    @Test
    void shouldNotCreateDigesterWithConstructor() {
        JavaClasses classes = getAllClasses();

        ArchRule noDigesterConstructorCalled = noClasses().that().dontHaveSimpleName("SecureDigester")
                .should().callConstructor(Digester.class)
                .orShould().callConstructor(Digester.class, SAXParser.class)
                .orShould().callConstructor(Digester.class, XMLReader.class)
                .orShould().callMethod(DigesterLoader.class, "newDigester");

        noDigesterConstructorCalled.check(classes);
    }

    /**
     * Test classes should not be public (Junit 5).
     */
    @Test
    void shouldNotUsePublicInTestCases() {
        JavaClasses classes = getAllClasses();

        ArchRule noPublicClasses = noClasses()
                .that().dontHaveModifier(JavaModifier.ABSTRACT)
                .and().haveSimpleNameEndingWith("Test")
                .and().haveNameNotMatching(getClass().getName())
                .and(dont(areAllowedPublicTestClasses()))
                .should().bePublic();

        noPublicClasses.check(classes);
    }

    /**
     * Returns a predicate that provides test classes that are allowed to be public.
     * 
     * @return allowed public test classes
     */
    protected DescribedPredicate<? super JavaClass> areAllowedPublicTestClasses() {
        return alwaysFalse();
    }

    /**
     * Methods or constructors that are annotated with {@link VisibleForTesting} must not be called by other classes.
     * These methods are meant to be {@code private}. Only test classes are allowed to call these methods.
     */
    @Test
    void shouldNotCallVisibleForTestingOutsideOfTest() {
        JavaClasses classes = getAllClasses();

        ArchRule noTestApiCalled = noClasses()
                .that().haveSimpleNameNotEndingWith("Test")
                .should().callCodeUnitWhere(ACCESS_IS_RESTRICTED_TO_TESTS);

        noTestApiCalled.check(classes);
    }

    /**
     * Prevents that classes use visible but forbidden API.
     */
    @Test
    void shouldNotUseForbiddenModules() {
        JavaClasses classes = getAllClasses();

        ArchRule restrictedApi = noClasses()
                .should().accessClassesThat().resideInAnyPackage(getForbiddenPackages());
//                .orShould().accessClassesThat().haveFullyQualifiedName(File.class.getName());

        restrictedApi.check(classes);
    }

    /**
     * Returns the packages that should not be accessed. 
     * 
     * @return the forbidden packages
     */
    protected String[] getForbiddenPackages() {
        return new String[]{"org.apache.commons.lang..", "javax.xml.bind.."};
    }

    /**
     * Matches if a call from outside the defining class uses a method or constructor annotated with {@link
     * VisibleForTesting}. There are two exceptions:
     * <ul>
     * <li>The method is called on the same class</li>
     * <li>The method is called in a method also annotated with {@link VisibleForTesting}</li>
     * </ul>
     */
    private static class AccessRestrictedToTests extends DescribedPredicate<JavaCall<?>> {
        AccessRestrictedToTests() {
            super("access is restricted to tests");
        }

        @Override
        public boolean apply(final JavaCall<?> input) {
            return input.getTarget().isAnnotatedWith(VisibleForTesting.class)
                    && !input.getOriginOwner().equals(input.getTargetOwner())
                    && !input.getOrigin().isAnnotatedWith(VisibleForTesting.class);
        }
    }
}
