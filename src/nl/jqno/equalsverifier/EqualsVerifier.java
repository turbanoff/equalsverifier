/*
 * Copyright 2009 Jan Ouwens
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.jqno.equalsverifier;

import static nl.jqno.equalsverifier.Assert.assertFalse;
import static nl.jqno.equalsverifier.Assert.assertTrue;
import static nl.jqno.equalsverifier.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import nl.jqno.instantiator.Instantiator;

/**
 * {@code EqualsVerifier} can be used in JUnit 4 unit tests to verify whether
 * the contract for the {@code equals} and {@code hashCode} methods is met. The
 * contracts are described in the Javadoc comments for the
 * {@link java.lang.Object} class.
 * <p>
 * By default, {@link EqualsVerifier} is as strict as possible. However,
 * several methods exist to relax the strictness. 
 * <p>
 * Use, within a JUnit 4 test method, as follows:<br>
 * - Create an instance of {@link EqualsVerifier}. Either call
 * {@link #forExamples(Object, Object, Object...)} to supply at least two
 * instances of the class under test that are not equal to one another, or
 * call {@link #forClass(Class)} to supply a reference to the class itself to
 * let the {@link EqualsVerifier} instantiate objects.<br>
 * - If the class under test is designed for inheritance, and the
 * {@code equals} and {@code hashCode} methods can be overridden, an instance
 * of the class is not permitted to be equal to an instance of a subclass, even
 * though all the relevant fields are equal. Call
 * {@link #withRedefinedSubclass(Class)} to supply a reference to such a
 * subclass, or call {@link #weakInheritanceCheck()} to disable the check.<br>
 * - Call {@link #allowMutableFields()} to allow mutable fields within
 * {@code equals} and {@code hashCode} methods.
 * - Call {@link #fieldsAreNeverNull()} if all fields of the class under test
 * are guaranteed never to be null. 
 * - Call {@link #verify()} to perform the actual verifications.
 * <p>
 * Example use:
 * 
 * <pre>{@code
 * EqualsVerifier.forClass(My.class).verify();
 * }</pre>
 * 
 * Or, with some of the checks disabled:
 * 
 * <pre>{@code
 * EqualsVerifier.forClass(My.class)
 *     .allowMutableFields()
 *     .fieldsAreNeverNull()
 *     .verify();
 * }</pre>
 * 
 * The following properties are verified:<br>
 * - Preconditions for {@link EqualsVerifier} itself.<br>
 * - Reflexivity and symmetry of the {@code equals} method.<br>
 * - Symmetry and transitivity of the {@code equals} method within an
 * inheritance hierarchy, when applicable.<br>
 * - Consistency (by repeatedly calling {@code equals}).<br>
 * - "Non-nullity".<br>
 * - That {@code equals}, {@code hashCode} and {@code toString} not be able to
 * throw {@link NullPointerException}. (Optional)<br>
 * - The {@code hashCode} contract.<br>
 * - That {@code equals} and {@code hashCode} be defined in terms of
 * the same fields.<br>
 * - Immutability of the fields in terms of which {@code equals} and
 * {@code hashCode} are defined. (Optional)<br>
 * - The finality of the fields in terms of which {@code equals} and
 * {@code hashCode} are defined. (Optional)<br>
 * - Finality of the class under test and of the {@code equals} method
 * itself, when applicable.
 * <p>
 * The last point warrants an explanation. While a class can define a perfect
 * {@code equals} method, a subclass can still break this contract, even
 * for its superclass. Therefore, a class should either be final, or the
 * {@code equals} contract should hold for its subclasses as well. This means
 * that an instance of a class should be equal to an instance of a subclass for
 * which all fields are equal.
 * <p>
 * Similarly, if {@code equals} is overridden, it can break the contract. So
 * either should the {@code equals} method be final, thereby guaranteeing its
 * adherence to the contracts for itself and all its subclasses, or instances
 * of the class may never be equal to instances of the subclass. This should be
 * tested by calling {@link #withRedefinedSubclass(Class)}, and by using the
 * {@link EqualsVerifier} for these subclasses separately as well. For an
 * example of an implementation of such redefined {@code equals} methods,
 * see {@code RedefinablePoint} in the {@link EqualsVerifier}'s unit
 * tests. See Chapter 28 of <i>Programming in Scala</i> (full reference below)
 * for an explanation of how and why this works.
 * <p>
 * By calling {@link #weakInheritanceCheck()}, the latter check can be
 * disabled. A call {@link #withRedefinedSubclass(Class)} is then no longer
 * necessary.
 * <p>
 * Dependencies:<br>
 * - JUnit 4: http://www.junit.org/<br>
 * - objenesis 1.1: http://code.google.com/p/objenesis/<br>
 * - cglib-nodep 2.2: http://cglib.sourceforge.net/<br>
 * <p>
 * The verifications are inspired by:<br>
 * - <i>Effective Java, Second Edition</i> by Joshua Bloch, Addison-Wesley,
 * 2008: Items 8 (<i>Obey the general contract when overriding
 * {@code equals}</i>) and 9 (<i>Always override {@code hashCode}
 * when you override {@code equals}</i>).<br>
 * - <i>Programming in Scala</i> by Martin Odersky, Lex Spoon and Bill
 * Venners, Artima Press, 2008: Chapter 28 (<i>Object equality</i>).<br>
 * - <i>JUnit Recipes</i> by J.B. Rainsberger, Manning, 2005: Appendix B.2
 * (<i>Strangeness and transitivity</i>).<br>
 * 
 * @param <T> The class under test.
 * 
 * @author Jan Ouwens
 * @see java.lang.Object#equals(Object)
 * @see java.lang.Object#hashCode()
 */
public final class EqualsVerifier<T> {
	private final Class<T> klass;
	private final List<T> examples;
	private final Instantiator<T> instantiator;
	
	private final EnumSet<Feature> features = EnumSet.noneOf(Feature.class);
	private Class<? extends T> redefinedSubclass = null;
	
	/**
	 * Factory method.
	 * 
	 * @param klass The class for which the {@code equals} method should be
	 * 				tested.
	 */
	public static <T> EqualsVerifier<T> forClass(Class<T> klass) {
		Instantiator<T> instantiator = Instantiator.forClass(klass);
		List<T> examples = new ArrayList<T>();

		return new EqualsVerifier<T>(klass, instantiator, examples);
	}
	
	/**
	 * Factory method.
	 * 
	 * @param first An instance of T.
	 * @param second Another instance of T, which is unequal to {@code first}.
	 * @param tail An array of instances of T, all of which are unequal to one
	 *		 		another and to {@code first} and {@code second}. May also
	 *				contain instances of subclasses of T.
	 */
	public static <T> EqualsVerifier<T> forExamples(T first, T second, T... tail) {
		if (first == null) {
			throw new IllegalArgumentException("First example is null");
		}
		if (second == null) {
			throw new IllegalArgumentException("Second example is null");
		}

		@SuppressWarnings("unchecked")
		Class<T> klass = (Class<T>)first.getClass();
		Instantiator<T> instantiator = Instantiator.forClass(klass);

		List<T> examples = new ArrayList<T>();
		examples.add(first);
		examples.add(second);
		if (tail != null) {
			examples.addAll(Arrays.asList(tail));
		}
		
		return new EqualsVerifier<T>(klass, instantiator, examples);
	}

	/**
	 * Private constructor. Call {@link #forClass(Class)} or
	 * {@link #forExamples(Object, Object, Object...)} instead.
	 */
	private EqualsVerifier(Class<T> klass, Instantiator<T> instantiator, List<T> examples) {
		this.klass = klass;
		this.examples = examples;
		this.instantiator = instantiator;
	}
	
	/**
	 * Adds features to the {@code EqualsVerifier}. These features modify the
	 * behaviour of the {@code EqualsVerifier}. See {@link Feature} to see what
	 * features are available.
	 * 
	 * @param features A list of features to add to the {@code EqualsVerifier}.
	 * @return {@code this}, for easy method chaining.
	 */
	public EqualsVerifier<T> with(Feature... features) {
		for (Feature feature : features) {
			this.features.add(feature);
		}
		return this;
	}
	
	/**
	 * Adds prefabricated values for instance fields of classes that
	 * EqualsVerifier cannot instantiate by itself.
	 * 
	 * @param <S> The class of the prefabricated values.
	 * @param otherKlass The class of the prefabricated values.
	 * @param first An instance of {@code S}.
	 * @param second An instance of {@code S}.
	 * @return {@code this}, for easy method chaining.
	 * @throws NullPointerException If either {@code first} or {@code second}
	 * 				is null.
	 * @throws IllegalArgumentException If {@code first} equals {@code second}.
	 */
	public <S> EqualsVerifier<T> withPrefabValues(Class<S> otherKlass, S first, S second) {
		instantiator.addPrefabValues(otherKlass, first, second);
		return this;
	}
	
	/**
	 * Supplies a reference to a subclass of T in which {@code equals} is
	 * overridden. Calling this method is mandatory if {@code equals} is not
	 * final and a strong verification is performed.
	 * <p>
	 * Note that, for each subclass that overrides {@code equals},
	 * {@link EqualsVerifier} should be used as well to verify its
	 * adherence to the contracts.
	 * 
	 * @param redefinedSubclass A subclass of T for which no instance can be
	 * 				equal to any instance of T.
	 * @return {@code this}, for easy method chaining.
	 * 
	 * @see Feature#WEAK_INHERITANCE_CHECK
	 */
	public EqualsVerifier<T> withRedefinedSubclass(Class<? extends T> redefinedSubclass) {
		this.redefinedSubclass = redefinedSubclass;
		return this;
	}
	
	/**
	 * Performs the verification of the contracts for {@code equals} and
	 * {@code hashCode}.
	 * 
	 * @throws AssertionError If the contract is not met, or if
	 * 				{@link EqualsVerifier}'s preconditions do not hold.
	 */
	public void verify() {
		ensureExamples();
		try {
			FieldsChecker<T> fieldsChecker = new FieldsChecker<T>(instantiator, features);
			ExamplesChecker<T> examplesChecker = new ExamplesChecker<T>(instantiator, examples);
			HierarchyChecker<T> hierarchyChecker = new HierarchyChecker<T>(instantiator, features, redefinedSubclass);
			
			fieldsChecker.checkNull();
			verifyPreconditions();
			examplesChecker.check();
			hierarchyChecker.check();
			
			fieldsChecker.check();
		}
		catch (Throwable e) {
			if (features.contains(Feature.VERBOSE)) {
				e.printStackTrace();
			}
			fail(e.getMessage());
		}
	}
	
	private void verifyPreconditions() {
		assertTrue("Precondition: no examples.", examples.size() > 0);
		for (T example : examples) {
			assertFalse("Precondition: one of the examples is null", example == null);
			assertTrue("Precondition: " + examples.get(0) + " and " + example + " are of different classes",
					klass.isAssignableFrom(example.getClass()));
		}
	}
	
	private void ensureExamples() {
		if (examples.size() > 1) {
			return;
		}
		
		T first = instantiator.instantiate();
		instantiator.scramble(first);
		T second = instantiator.instantiate();
		instantiator.scramble(second);
		instantiator.scramble(second);
		
		examples.add(first);
		examples.add(second);
	}
}
