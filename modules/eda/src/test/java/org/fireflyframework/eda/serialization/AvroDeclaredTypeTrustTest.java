/*
 * Copyright 2024-2026 Firefly Software Foundation
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

package org.fireflyframework.eda.serialization;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.reflect.*;
import org.apache.avro.specific.*;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class AvroDeclaredTypeTrustTest {
    public enum Kind { FIRST, SECOND }
    public static class Child { public String name; public Child() {} Child(String name) { this.name = name; } }
    public static class Other extends Child { public Other() {} }
    public static class Value {
        public List<Child> children;
        public Set<String> labels;
        public Map<String, Child> values;
        public Child[] array;
        public Kind kind;
        public byte[] bytes;
        public ByteBuffer buffer;
        public Value() {}
    }
    public static class Node { public String name; @Nullable public Node next; public Node() {} }
    public static class UnionValue { @Union({Child.class, String.class}) public Object item; public UnionValue() {} }
    public static class Raw { public List values; public Raw() {} }
    public static class Unknown { public Object value; public Unknown() {} }
    public static class Typed { public Child value; public Typed() {} }
    public static class IntMap { public Map<Integer, String> values; public IntMap() {} }
    public static class CustomList extends ArrayList<String> {}
    public static class CustomContainer { public CustomList values; public CustomContainer() {} }
    public static class Spoof {
        @AvroSchema("{\"type\":\"string\",\"java-class\":\"java.lang.Runtime\"}")
        public String value;
        public Spoof() {}
    }
    @FixedSize(2)
    public static class Fixed extends SpecificFixed {
        public static final Schema SCHEMA$ = Schema.createFixed("Fixed", null, "org.fireflyframework.eda.serialization.AvroDeclaredTypeTrustTest", 2);
        public Fixed() {} public Fixed(byte[] data) { super(data); }
        public Schema getSchema() { return SCHEMA$; }
        public void writeExternal(java.io.ObjectOutput out) throws java.io.IOException { out.write(bytes()); }
        public void readExternal(java.io.ObjectInput in) throws java.io.IOException { byte[] bytes = new byte[2]; in.readFully(bytes); bytes(bytes); }
    }
    public static class SpecificChild extends SpecificRecordBase {
        public static final Schema SCHEMA$ = SchemaBuilder.record("SpecificChild").namespace("org.fireflyframework.eda.serialization.AvroDeclaredTypeTrustTest").fields().requiredString("name").endRecord();
        public CharSequence name; public SpecificChild() {} SpecificChild(String name) { this.name=name; }
        public Schema getSchema() { return SCHEMA$; }
        public Object get(int index) { if(index!=0)throw new IndexOutOfBoundsException();return name; }
        public void put(int index,Object value) { if(index!=0)throw new IndexOutOfBoundsException();name=(CharSequence)value; }
    }
    public static class Specific extends SpecificRecordBase {
        public static final Schema SCHEMA$ = SchemaBuilder.record("Specific").namespace("org.fireflyframework.eda.serialization.AvroDeclaredTypeTrustTest").fields()
            .requiredString("name").name("child").type().unionOf().nullType().and().type(SpecificChild.SCHEMA$).endUnion().nullDefault()
            .name("fixed").type(Fixed.SCHEMA$).noDefault().endRecord();
        public CharSequence name; public Object child; public Fixed fixed;
        public Schema getSchema() { return SCHEMA$; }
        public Object get(int index) { return switch(index) { case 0 -> name; case 1 -> child; case 2 -> fixed; default -> throw new IndexOutOfBoundsException(); }; }
        public void put(int index, Object value) { switch(index) { case 0 -> name = (CharSequence)value; case 1 -> child = value; case 2 -> fixed = (Fixed)value; default -> throw new IndexOutOfBoundsException(); } }
    }
    private final AvroMessageSerializer serializer = new AvroMessageSerializer();
    private Value value() { Value v = new Value(); v.children = List.of(new Child("list")); v.labels = Set.of("label"); v.values = Map.of("key", new Child("map")); v.array = new Child[]{new Child("array")}; v.kind = Kind.SECOND; v.bytes = new byte[]{0,1,-1}; v.buffer = ByteBuffer.wrap(new byte[]{3,4}); return v; }
    @Test void nestedCollectionsEnumsArraysAndBytesRoundTrip() { Value a = serializer.deserialize(serializer.serialize(value()), Value.class); assertThat(a.children.getFirst().name).isEqualTo("list"); assertThat(a.labels).containsExactly("label"); assertThat(a.values.get("key").name).isEqualTo("map"); assertThat(a.array[0].name).isEqualTo("array"); assertThat(a.kind).isEqualTo(Kind.SECOND); assertThat(a.bytes).containsExactly(0,1,-1); assertThat(a.buffer).isEqualTo(ByteBuffer.wrap(new byte[]{3,4})); }
    @Test void emptyContainersRoundTrip() { Value v=value(); v.children=List.of();v.labels=Set.of();v.values=Map.of();v.array=new Child[0];Value a=serializer.deserialize(serializer.serialize(v),Value.class);assertThat(a.children).isEmpty();assertThat(a.labels).isEmpty();assertThat(a.values).isEmpty();assertThat(a.array).isEmpty(); }
    @Test void nullAndRawBytesStayCompatible() { byte[] b={1,2}; assertThat(serializer.serialize(null)).isEmpty();assertThat(serializer.deserialize((byte[])null,Value.class)).isNull();assertThat(serializer.deserialize(new byte[0],Object.class)).isNull();assertThat(serializer.serialize(b)).isSameAs(b);assertThat(serializer.deserialize(b,byte[].class)).isSameAs(b); }
    @Test void concreteObjectOptInIsExactlyOneRoot() { var bound=new AvroMessageSerializer(new AvroTypeBindings(Set.of(),Child.class));byte[] b=bound.serialize(new Child("bound"));assertThat(((Child)bound.deserialize(b,Object.class)).name).isEqualTo("bound");assertThatThrownBy(()->serializer.deserialize(b,Object.class)).isInstanceOf(SerializationException.class); }
    @Test void rawGenericIsRefused() { assertThat(serializer.canSerialize(Raw.class)).isFalse();assertThatThrownBy(()->serializer.serialize(new Raw())).isInstanceOf(SerializationException.class); }
    @Test void unknownObjectFieldIsRefused() { assertThat(serializer.canSerialize(Unknown.class)).isFalse();assertThatThrownBy(()->serializer.serialize(new Unknown())).isInstanceOf(SerializationException.class); }
    @Test void unapprovedSubclassIsRefused() { Typed v=new Typed();v.value=new Other();v.value.name="other";assertThatThrownBy(()->serializer.serialize(v)).isInstanceOf(SerializationException.class); }
    @Test void nonStringMapIsRefused() { assertThatThrownBy(()->serializer.serialize(new IntMap())).isInstanceOf(SerializationException.class); }
    @Test void customContainerIsRefused() { assertThatThrownBy(()->serializer.serialize(new CustomContainer())).isInstanceOf(SerializationException.class); }
    @Test void maliciousClassHintIsRefused() { Spoof v=new Spoof();v.value="x";assertThatThrownBy(()->serializer.serialize(v)).isInstanceOf(SerializationException.class); }
    @Test void finiteUnionUsesDeclaredTokens() { UnionValue v=new UnionValue();v.item=new Child("union");UnionValue a=serializer.deserialize(serializer.serialize(v),UnionValue.class);assertThat(((Child)a.item).name).isEqualTo("union");v.item=new Other();assertThatThrownBy(()->serializer.serialize(v)).isInstanceOf(SerializationException.class); }
    @Test void actualValueCycleIsRefusedBeforeSdkRecursion() { Node n=new Node();n.name="root";n.next=n;assertThatThrownBy(()->serializer.serialize(n)).isInstanceOf(SerializationException.class); }
    @Test void finiteRecursiveValuesRoundTrip() { Node n=new Node();n.name="root";n.next=new Node();n.next.name="leaf";Node a=serializer.deserialize(serializer.serialize(n),Node.class);assertThat(a.next.name).isEqualTo("leaf");assertThat(a.next.next).isNull(); }
    @Test void valueDepthIsBounded() { Node n=new Node();n.name="0";Node current=n;for(int i=0;i<34;i++){current.next=new Node();current=current.next;current.name="x";}assertThatThrownBy(()->serializer.serialize(n)).isInstanceOf(SerializationException.class); }
    @Test void sharedAcyclicChildIsAllowed() { Value v=value();v.array=new Child[]{v.children.getFirst(),v.children.getFirst()};Value a=serializer.deserialize(serializer.serialize(v),Value.class);assertThat(a.array).extracting(c->c.name).containsExactly("list","list"); }
    @Test void generatedSpecificNestedBranchesRequireExplicitTokens() { Specific v=new Specific();v.name="specific";v.child=new SpecificChild("nested");v.fixed=new Fixed(new byte[]{7,8});assertThatThrownBy(()->serializer.serialize(v)).isInstanceOf(SerializationException.class);var bound=new AvroMessageSerializer(new AvroTypeBindings(Set.of(SpecificChild.class),null));Specific a=bound.deserialize(bound.serialize(v),Specific.class);assertThat(a.name.toString()).isEqualTo("specific");assertThat(((SpecificChild)a.child).name.toString()).isEqualTo("nested");assertThat(a.fixed.bytes()).containsExactly(7,8); }
    @Test void genericRecordEncodingWithoutHintsRemainsSupported() { Schema schema=SchemaBuilder.record("Plain").fields().requiredString("value").endRecord();var record=new GenericData.Record(schema);record.put("value","generic");assertThat(serializer.serialize(record)).isNotEmpty();assertThatThrownBy(()->serializer.deserialize(new byte[]{1},org.apache.avro.generic.GenericRecord.class)).isInstanceOf(SerializationException.class); }
    @Test void genericRecordCannotGrantJavaClassAuthority() { Schema schema=SchemaBuilder.record("Plain").fields().requiredString("value").endRecord();schema.addProp("java-class",Child.class.getName());var record=new GenericData.Record(schema);record.put("value","generic");assertThatThrownBy(()->serializer.serialize(record)).isInstanceOf(SerializationException.class); }
    @Test void capabilityPreservesPrimitiveEnumInterfaceAndTimeExclusions() { assertThat(serializer.canSerialize(int.class)).isFalse();assertThat(serializer.canSerialize(Kind.class)).isFalse();assertThat(serializer.canSerialize(Runnable.class)).isFalse();assertThat(serializer.canSerialize(java.time.Instant.class)).isFalse(); }
    @Test void unsupportedObjectBindingIsRefused() { assertThatThrownBy(()->new AvroMessageSerializer(new AvroTypeBindings(Set.of(),Object.class))).isInstanceOf(IllegalArgumentException.class); }
    @Test void bindingsAreSnapshotted() { Set<Class<?>> set=new HashSet<>();set.add(Child.class);var b=new AvroTypeBindings(set,null);set.clear();assertThat(b.schemaTypes()).containsExactly(Child.class);assertThatThrownBy(()->b.schemaTypes().clear()).isInstanceOf(UnsupportedOperationException.class); }
    @Test void truncatedArrayIsRejected() { byte[] b=serializer.serialize(value());assertThatThrownBy(()->serializer.deserialize(Arrays.copyOf(b,b.length-2),Value.class)).isInstanceOf(SerializationException.class); }
    @Test void interleavedInstancesDoNotLeakBindingsOrGlobalPolicy() throws Exception { boolean before=stockPermits();var allowed=new AvroMessageSerializer(new AvroTypeBindings(Set.of(),Child.class));try(var pool=Executors.newFixedThreadPool(4)){List<Callable<Boolean>> tasks=new ArrayList<>();for(int i=0;i<100;i++)tasks.add(()->{byte[] b=allowed.serialize(new Child("x"));assertThat(((Child)allowed.deserialize(b,Object.class)).name).isEqualTo("x");assertThatThrownBy(()->serializer.deserialize(b,Object.class)).isInstanceOf(SerializationException.class);return true;});for(var f:pool.invokeAll(tasks))assertThat(f.get()).isTrue();}assertThat(stockPermits()).isEqualTo(before); }
    private static int encodingConstructors;
    public static class Encoding extends CustomEncoding<Child> {
        public Encoding() { encodingConstructors++; }
        protected void write(Object value, org.apache.avro.io.Encoder out) { throw new AssertionError(); }
        protected Child read(Object reuse, org.apache.avro.io.Decoder in) { throw new AssertionError(); }
    }
    public static class Encoded { @AvroEncode(using=Encoding.class) public Child value; public Encoded() {} }
    @AvroMeta(key="java-class", value="org.fireflyframework.eda.serialization.AvroDeclaredTypeTrustTest$Child")
    public static class WrongHint { public String value; public WrongHint() {} }
    public static class Many { public List<Child> values; public Many() {} }
    public static class Empty { public Empty() {} }
    public static class EmptyArray { public Empty[] values; public EmptyArray() {} }
    @Test void customEncodingIsRefusedBeforeConstruction() { Encoded v=new Encoded();v.value=new Child("x");assertThatThrownBy(()->serializer.serialize(v)).isInstanceOf(SerializationException.class);assertThat(encodingConstructors).isZero(); }
    @Test void extraKnownTokenAtWrongSchemaLocationIsRefused() { WrongHint v=new WrongHint();v.value="x";var bound=new AvroMessageSerializer(new AvroTypeBindings(Set.of(Child.class),null));assertThatThrownBy(()->bound.serialize(v)).isInstanceOf(SerializationException.class); }
    @Test void genericValueCycleIsRefused() { Schema schema=Schema.createRecord("Cycle",null,"probe",false);schema.setFields(List.of(new Schema.Field("next",Schema.createUnion(Schema.create(Schema.Type.NULL),schema),null,(Object)null)));var value=new GenericData.Record(schema);value.put("next",value);assertThatThrownBy(()->serializer.serialize(value)).isInstanceOf(SerializationException.class); }
    @Test void actualValueVisitCountIsBounded() { Many v=new Many();v.values=Collections.nCopies(100001,new Child("x"));assertThatThrownBy(()->serializer.serialize(v)).isInstanceOf(SerializationException.class); }
    @Test void zeroWidthArraysRoundTripAndRejectTruncatedCounts() { EmptyArray v=new EmptyArray();v.values=new Empty[]{new Empty(),new Empty()};EmptyArray a=serializer.deserialize(serializer.serialize(v),EmptyArray.class);assertThat(a.values).hasSize(2);assertThatThrownBy(()->serializer.deserialize(new byte[]{(byte)0x80},EmptyArray.class)).isInstanceOf(SerializationException.class); }
    @Test void scalarGoldenWireBytesRemainStable() { assertThat(serializer.serialize(new Child("x"))).containsExactly(2,120); }
    @Test void typeDepthIsBoundedBeforeSdkUse() { assertThat(serializer.canSerialize(Deep0.class)).isFalse();assertThatThrownBy(()->serializer.serialize(new Deep0())).isInstanceOf(SerializationException.class); }
    public static class Deep0 { public Deep1 value; public Deep0() {} }
    public static class Deep1 { public Deep2 value; public Deep1() {} }
    public static class Deep2 { public Deep3 value; public Deep2() {} }
    public static class Deep3 { public Deep4 value; public Deep3() {} }
    public static class Deep4 { public Deep5 value; public Deep4() {} }
    public static class Deep5 { public Deep6 value; public Deep5() {} }
    public static class Deep6 { public Deep7 value; public Deep6() {} }
    public static class Deep7 { public Deep8 value; public Deep7() {} }
    public static class Deep8 { public Deep9 value; public Deep8() {} }
    public static class Deep9 { public Deep10 value; public Deep9() {} }
    public static class Deep10 { public Deep11 value; public Deep10() {} }
    public static class Deep11 { public Deep12 value; public Deep11() {} }
    public static class Deep12 { public Deep13 value; public Deep12() {} }
    public static class Deep13 { public Deep14 value; public Deep13() {} }
    public static class Deep14 { public Deep15 value; public Deep14() {} }
    public static class Deep15 { public Deep16 value; public Deep15() {} }
    public static class Deep16 { public Deep17 value; public Deep16() {} }
    public static class Deep17 { public Deep18 value; public Deep17() {} }
    public static class Deep18 { public Deep19 value; public Deep18() {} }
    public static class Deep19 { public Deep20 value; public Deep19() {} }
    public static class Deep20 { public Deep21 value; public Deep20() {} }
    public static class Deep21 { public Deep22 value; public Deep21() {} }
    public static class Deep22 { public Deep23 value; public Deep22() {} }
    public static class Deep23 { public Deep24 value; public Deep23() {} }
    public static class Deep24 { public Deep25 value; public Deep24() {} }
    public static class Deep25 { public Deep26 value; public Deep25() {} }
    public static class Deep26 { public Deep27 value; public Deep26() {} }
    public static class Deep27 { public Deep28 value; public Deep27() {} }
    public static class Deep28 { public Deep29 value; public Deep28() {} }
    public static class Deep29 { public Deep30 value; public Deep29() {} }
    public static class Deep30 { public Deep31 value; public Deep30() {} }
    public static class Deep31 { public Deep32 value; public Deep31() {} }
    public static class Deep32 { public Deep33 value; public Deep32() {} }
    public static class Deep33 { public Deep34 value; public Deep33() {} }
    public static class Deep34 { public String value; public Deep34() {} }
    public static class TooManyBranches { @Union({Child.class,String.class,Integer.class,Long.class,Float.class,Double.class,Boolean.class,Byte.class,Short.class,Character.class,Kind.class,Node.class,Value.class,Typed.class,Specific.class,SpecificChild.class,Fixed.class}) public Object value; public TooManyBranches() {} }
    @Test void unionBranchCountIsBounded() { assertThatThrownBy(()->serializer.serialize(new TooManyBranches())).isInstanceOf(SerializationException.class); }
    @Test void additionalTokenCountIsBounded() { assertThatThrownBy(()->new AvroTypeBindings(Set.of(Child.class,String.class,Integer.class,Long.class,Float.class,Double.class,Boolean.class,Byte.class,Short.class,Character.class,Kind.class,Node.class,Value.class,Typed.class,Specific.class,SpecificChild.class,Fixed.class),null)).isInstanceOf(IllegalArgumentException.class); }
    @Test void declaredFieldCountIsBounded() { assertThat(serializer.canSerialize(generated("TooManyFields", java.util.stream.IntStream.range(0,4097).mapToObj(i->"public int f"+i+";").collect(java.util.stream.Collectors.joining())) )).isFalse(); }
    @Test void distinctDeclaredTypeCountIsBounded() { assertThat(serializer.canSerialize(generated("TooManyTypes", java.util.stream.IntStream.range(0,130).mapToObj(i->"public java.util.List<Bound"+i+"> f"+i+";public static class Bound"+i+"{}").collect(java.util.stream.Collectors.joining())) )).isFalse(); }
    @Test void identicalNamesFromDifferentLoadersAreRejected() throws Exception { byte[] bytes;try(var in=Child.class.getResourceAsStream("AvroDeclaredTypeTrustTest$Child.class")){bytes=Objects.requireNonNull(in).readAllBytes();}class Loader extends ClassLoader { Class<?> define(byte[] code) { return defineClass(null,code,0,code.length); } }Class<?> duplicate=new Loader().define(bytes);assertThat(duplicate.getName()).isEqualTo(Child.class.getName());assertThat(duplicate).isNotSameAs(Child.class);assertThatThrownBy(()->new AvroMessageSerializer(new AvroTypeBindings(Set.of(duplicate),Child.class))).isInstanceOf(IllegalArgumentException.class); }
    @Test void trustPropertiesStayUnchanged() { String classes=System.getProperty("org.apache.avro.SERIALIZABLE_CLASSES"),packages=System.getProperty("org.apache.avro.SERIALIZABLE_PACKAGES");serializer.deserialize(serializer.serialize(new Child("x")),Child.class);assertThat(System.getProperty("org.apache.avro.SERIALIZABLE_CLASSES")).isEqualTo(classes);assertThat(System.getProperty("org.apache.avro.SERIALIZABLE_PACKAGES")).isEqualTo(packages); }
    private static Class<?> generated(String name, String body) {
        var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();
        Map<String, java.io.ByteArrayOutputStream> classes = new HashMap<>();
        var source = new javax.tools.SimpleJavaFileObject(java.net.URI.create("string:///" + name + ".java"), javax.tools.JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return "public class " + name + " {" + body + "}"; }
        };
        try (var manager = new javax.tools.ForwardingJavaFileManager<>(compiler.getStandardFileManager(null, Locale.ROOT, java.nio.charset.StandardCharsets.UTF_8)) {
            @Override public javax.tools.JavaFileObject getJavaFileForOutput(javax.tools.JavaFileManager.Location location, String className, javax.tools.JavaFileObject.Kind kind, javax.tools.FileObject sibling) {
                return new javax.tools.SimpleJavaFileObject(java.net.URI.create("memory:///" + className + ".class"), kind) {
                    @Override public java.io.OutputStream openOutputStream() { var bytes = new java.io.ByteArrayOutputStream(); classes.put(className,bytes); return bytes; }
                };
            }
        }) {
            assertThat(compiler.getTask(null,manager,null,List.of("-proc:none","--release","25"),null,List.of(source)).call()).isTrue();
        } catch (java.io.IOException error) { throw new AssertionError(error); }
        class Loader extends ClassLoader {
            @Override protected Class<?> findClass(String className) throws ClassNotFoundException {
                var bytes=classes.get(className);
                if(bytes==null)throw new ClassNotFoundException(className);
                byte[] code=bytes.toByteArray();return defineClass(className,code,0,code.length);
            }
            Class<?> root() throws ClassNotFoundException { return findClass(name); }
        }
        try { return new Loader().root(); } catch (ClassNotFoundException error) { throw new AssertionError(error); }
    }
    public static class SmallNumbers { public byte small; public short medium; public char letter; public SmallNumbers() {} }
    @Test void primitiveIntBackedTypesPreserveTheirDeclaredShape() { SmallNumbers value=new SmallNumbers();value.small=7;value.medium=123;value.letter='Q';SmallNumbers actual=serializer.deserialize(serializer.serialize(value),SmallNumbers.class);assertThat(actual.small).isEqualTo((byte)7);assertThat(actual.medium).isEqualTo((short)123);assertThat(actual.letter).isEqualTo('Q'); }
    public static class ConcreteContainers { public LinkedHashMap<String,Child> ordered; public HashMap<String,Child> map; public LinkedHashSet<String> labels; public ConcreteContainers() {} }
    @Test void concreteCanonicalCollectionDeclarationsGetAssignableFactories() { ConcreteContainers v=new ConcreteContainers();v.ordered=new LinkedHashMap<>(Map.of("a",new Child("ordered")));v.map=new HashMap<>(Map.of("b",new Child("map")));v.labels=new LinkedHashSet<>(Set.of("label"));ConcreteContainers a=serializer.deserialize(serializer.serialize(v),ConcreteContainers.class);assertThat(a.ordered.get("a").name).isEqualTo("ordered");assertThat(a.map.get("b").name).isEqualTo("map");assertThat(a.labels).containsExactly("label"); }
    public static class Logical { public UUID id; public java.time.LocalDate date; public java.time.Instant at; public java.math.BigDecimal amount; public Logical() {} }
    @Test void standardLogicalConversionsRemainOperationLocal() { Logical v=new Logical();v.id=UUID.fromString("123e4567-e89b-12d3-a456-426614174000");v.date=java.time.LocalDate.of(2026,10,30);v.at=java.time.Instant.parse("2026-10-30T12:00:00.123456Z");v.amount=new java.math.BigDecimal("12.34");Logical a=serializer.deserialize(serializer.serialize(v),Logical.class);assertThat(a.id).isEqualTo(v.id);assertThat(a.date).isEqualTo(v.date);assertThat(a.at).isEqualTo(v.at);assertThat(a.amount).isEqualTo(v.amount); }
    public static class HiddenStatic extends Typed { public static Child value; public HiddenStatic() {} }
    @Test void hiddenStaticFieldCannotHideUnapprovedInstanceValue() { HiddenStatic value=new HiddenStatic();HiddenStatic.value=new Child("safe");((Typed)value).value=new Other();((Typed)value).value.name="unapproved";try { assertThatThrownBy(()->serializer.serialize(value)).isInstanceOf(SerializationException.class); } finally { HiddenStatic.value=null; } }
    @Test void avroRecordCapabilityTokensRemainCompatible() { assertThat(serializer.canSerialize(org.apache.avro.generic.GenericRecord.class)).isTrue();assertThat(serializer.canSerialize(SpecificRecord.class)).isTrue(); }
    public static class SpecificMap extends SpecificRecordBase {
        public static final Schema SCHEMA$ = SchemaBuilder.record("SpecificMap").namespace("org.fireflyframework.eda.serialization.AvroDeclaredTypeTrustTest").fields().name("values").type().map().values(SpecificChild.SCHEMA$).noDefault().endRecord();
        public Map<String,SpecificChild> values;
        public Schema getSchema() { return SCHEMA$; }
        public Object get(int index) { if(index!=0)throw new IndexOutOfBoundsException();return values; }
        @SuppressWarnings("unchecked") public void put(int index,Object value) { if(index!=0)throw new IndexOutOfBoundsException();values=(Map<String,SpecificChild>)value; }
    }
    @Test void generatedSpecificStringMapKeysUseTheDeclaredContract() { SpecificMap value=new SpecificMap();value.values=Map.of("key",new SpecificChild("child"));SpecificMap actual=serializer.deserialize(serializer.serialize(value),SpecificMap.class);assertThat(actual.values.get("key").name.toString()).isEqualTo("child");assertThat(actual.values.keySet()).allMatch(key->key.getClass()==String.class); }
    public static class DurationValue { public org.apache.avro.util.TimePeriod period; public DurationValue() {} }
    @Test void standardDurationUsesItsValidatedFixedPhysicalSchema() { DurationValue value=new DurationValue();value.period=org.apache.avro.util.TimePeriod.of(1,2,3);DurationValue actual=serializer.deserialize(serializer.serialize(value),DurationValue.class);assertThat(actual.period).isEqualTo(value.period); }
    public static class SpecificLogical extends SpecificRecordBase {
        public static final Schema SCHEMA$ = SchemaBuilder.record("SpecificLogical").namespace("org.fireflyframework.eda.serialization.AvroDeclaredTypeTrustTest").fields()
                .name("period").type(org.apache.avro.LogicalTypes.duration().addToSchema(Schema.createFixed("Period",null,"declared.logical",12))).noDefault()
                .name("id").type(org.apache.avro.LogicalTypes.uuid().addToSchema(Schema.createFixed("Id",null,"declared.logical",16))).noDefault().endRecord();
        public org.apache.avro.util.TimePeriod period; public UUID id;
        public Schema getSchema() { return SCHEMA$; }
        public Object get(int index) { return switch(index) { case 0 -> period; case 1 -> id; default -> throw new IndexOutOfBoundsException(); }; }
        public void put(int index,Object value) { switch(index) { case 0 -> period=(org.apache.avro.util.TimePeriod)value;case 1 -> id=(UUID)value;default -> throw new IndexOutOfBoundsException(); } }
        @Override public org.apache.avro.Conversion<?> getConversion(int index) { return switch(index) { case 0 -> new org.apache.avro.Conversions.DurationConversion();case 1 -> new org.apache.avro.Conversions.UUIDConversion();default -> throw new IndexOutOfBoundsException(); }; }
    }
    @Test void specificLogicalFixedValuesUseGenericPhysicalCarrierAndExactConversions() { SpecificLogical value=new SpecificLogical();value.period=org.apache.avro.util.TimePeriod.of(3,2,1);value.id=UUID.fromString("123e4567-e89b-12d3-a456-426614174000");SpecificLogical actual=serializer.deserialize(serializer.serialize(value),SpecificLogical.class);assertThat(actual.period).isEqualTo(value.period);assertThat(actual.id).isEqualTo(value.id); }
    private boolean stockPermits() { try { new ReflectData().getClass(ReflectData.get().getSchema(Child.class));return true; } catch(SecurityException denied) { return false; } }
}
