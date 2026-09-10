package dev.skidfuscator.obfuscator.polymorphic.model;

public interface Engine {
	Context transform(String text) throws Exception;
}
