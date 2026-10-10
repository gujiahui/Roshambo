"""
Generate gesture icons for the Roshambo app.
Creates clean, modern icons with transparent background.

Output: PNG files in app/src/main/res/drawable-*
"""

from PIL import Image, ImageDraw
import os
import math

# Project theme colors
ROCK_COLOR = (58, 134, 255, 255)      # Blue
SCISSORS_COLOR = (255, 107, 107, 255)  # Red
PAPER_COLOR = (43, 182, 115, 255)      # Green
ICON_COLOR = (26, 26, 26, 255)         # Ink black

# Sizes for different densities
SIZES = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192,
}

def draw_rock(draw, size, color):
    """Draw a rock (fist) icon."""
    margin = size // 6
    rock_width = size - 2 * margin
    rock_height = int(rock_width * 0.75)
    
    # Main rock body (rounded rectangle)
    x0 = margin
    y0 = margin
    x1 = size - margin
    y1 = y0 + rock_height
    
    # Draw rounded rectangle for rock
    draw.rounded_rectangle([x0, y0, x1, y1], radius=size//8, fill=color)
    
    # Add some texture lines
    line_color = (255, 255, 255, 100)
    for i in range(3):
        y = y0 + (i + 1) * rock_height // 4
        draw.line([(x0 + size//10, y), (x1 - size//10, y)], fill=line_color, width=max(1, size//48))

def draw_scissors(draw, size, color):
    """Draw a scissors icon."""
    margin = size // 6
    center_x = size // 2
    center_y = size // 2
    
    # Two crossed lines (scissors blades)
    line_width = max(2, size // 24)
    
    # Blade 1 (top-left to bottom-right)
    draw.line([(margin, margin), (size - margin, size - margin)], fill=color, width=line_width)
    
    # Blade 2 (top-right to bottom-left)
    draw.line([(size - margin, margin), (margin, size - margin)], fill=color, width=line_width)
    
    # Finger holes
    hole_radius = size // 12
    draw.ellipse([margin - hole_radius, size - margin - hole_radius, 
                  margin + hole_radius, size - margin + hole_radius], 
                 outline=color, width=line_width)
    draw.ellipse([size - margin - hole_radius, margin - hole_radius, 
                  size - margin + hole_radius, margin + hole_radius], 
                 outline=color, width=line_width)

def draw_paper(draw, size, color):
    """Draw a paper (open hand) icon."""
    margin = size // 6
    
    # Palm (rounded rectangle)
    palm_width = size - 2 * margin
    palm_height = int(palm_width * 0.6)
    x0 = margin
    y0 = margin + size // 6
    x1 = size - margin
    y1 = y0 + palm_height
    
    draw.rounded_rectangle([x0, y0, x1, y1], radius=size//10, fill=color)
    
    # Fingers (4 rectangles)
    finger_width = palm_width // 5
    finger_height = size // 4
    finger_gap = (palm_width - 4 * finger_width) // 3
    
    for i in range(4):
        fx = x0 + i * (finger_width + finger_gap)
        fy = y0 - finger_height + size // 12
        draw.rounded_rectangle([fx, fy, fx + finger_width, y0 + size//12], 
                               radius=size//16, fill=color)

def generate_icon(gesture, size, color):
    """Generate a single icon."""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    if gesture == 'rock':
        draw_rock(draw, size, color)
    elif gesture == 'scissors':
        draw_scissors(draw, size, color)
    elif gesture == 'paper':
        draw_paper(draw, size, color)
    
    return img

def main():
    output_base = "app/src/main/res"
    
    gestures = {
        'rock': ROCK_COLOR,
        'scissors': SCISSORS_COLOR,
        'paper': PAPER_COLOR,
    }
    
    for gesture, color in gestures.items():
        for density, size in SIZES.items():
            drawable_dir = f"{output_base}/drawable-{density}"
            os.makedirs(drawable_dir, exist_ok=True)
            
            img = generate_icon(gesture, size, color)
            path = f"{drawable_dir}/ic_gesture_{gesture}.png"
            img.save(path, 'PNG')
            print(f"Generated {path} ({size}x{size})")
    
    print("\nAll gesture icons generated successfully!")

if __name__ == "__main__":
    main()
