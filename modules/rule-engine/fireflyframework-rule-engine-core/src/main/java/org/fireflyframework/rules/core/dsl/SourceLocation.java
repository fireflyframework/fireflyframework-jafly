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

package org.fireflyframework.rules.core.dsl;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a location in the source code for error reporting and debugging.
 * Provides precise line and column information for AST nodes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceLocation {
    
    /**
     * Line number (1-based)
     */
    private int line;
    
    /**
     * Column number (1-based)
     */
    private int column;
    
    /**
     * Starting position in the source text (0-based)
     */
    private int startPosition;
    
    /**
     * Ending position in the source text (0-based)
     */
    private int endPosition;
    
    /**
     * The source text that this location refers to
     */
    private String sourceText;

    /**
     * Get the line number
     */
    public int getLine() {
        return line;
    }

    /**
     * Get the column number
     */
    public int getColumn() {
        return column;
    }
    
    /**
     * Create a source location for a single position
     */
    public static SourceLocation at(int line, int column) {
        return new SourceLocation(line, column, 0, 0, null);
    }
    
    /**
     * Create a source location spanning from start to end
     */
    public static SourceLocation span(int startLine, int startColumn, int endLine, int endColumn) {
        return new SourceLocation(startLine, startColumn, 0, 0, null);
    }
    
    /**
     * Create a source location with position information
     */
    public static SourceLocation range(int line, int column, int startPos, int endPos, String sourceText) {
        return new SourceLocation(line, column, startPos, endPos, sourceText);
    }
    
    /**
     * Get a human-readable representation of this location
     */
    @Override
    public String toString() {
        return String.format("line %d, column %d", line, column);
    }
    
    /**
     * Get the text excerpt around this location for error reporting
     */
    public String getContextualText(int contextLines) {
        if (sourceText == null || sourceText.isEmpty()) {
            return "";
        }
        
        String[] lines = sourceText.split("\n");
        if (line <= 0 || line > lines.length) {
            return "";
        }
        
        int startLine = Math.max(0, line - 1 - contextLines);
        int endLine = Math.min(lines.length - 1, line - 1 + contextLines);
        
        StringBuilder context = new StringBuilder();
        for (int i = startLine; i <= endLine; i++) {
            if (i == line - 1) {
                // Highlight the error line
                context.append(">>> ").append(lines[i]).append("\n");
                // Add pointer to the column
                context.append("    ");
                for (int j = 0; j < column - 1; j++) {
                    context.append(" ");
                }
                context.append("^\n");
            } else {
                context.append("    ").append(lines[i]).append("\n");
            }
        }
        
        return context.toString();
    }

    /**
     * Check if this location has context text available
     *
     * @return true if context text is available
     */
    public boolean hasContext() {
        return sourceText != null && !sourceText.trim().isEmpty();
    }

    /**
     * Get the context text around this location
     *
     * @return the context text
     */
    public String getContextText() {
        if (!hasContext()) {
            return "";
        }

        // Extract a reasonable amount of context around the location
        int contextStart = Math.max(0, startPosition - 20);
        int contextEnd = Math.min(sourceText.length(), endPosition + 20);

        return sourceText.substring(contextStart, contextEnd);
    }
}
