import pandas as pd
import re
from datetime import datetime

# Sample: assume you've saved your raw data in 'taal_raw_data.csv'
df = pd.read_csv(f'taal_volcano_bulletin_data.csv')

# --- Clean and transform ---

# Convert 'Date' to datetime
df['Date'] = pd.to_datetime(df['Date'], format='%d %B %Y')


# --- FIXED: Eruption: Convert to number of events ---
def parse_eruption_info(text):
    text = str(text).lower()
    result = {
        "Eruption_Count": 0,
        #"Eruption_Type": None,
        "Eruption_Severity_Score": 0,
        "Total_Eruption_Duration_Min": 0,
        "Avg_Eruption_Duration_Min": 0,
    }

    # FIXED: More precise eruption counting - avoid capturing unrelated "events"
    # Look specifically for eruption-related patterns
    eruption_patterns = [
        r'(\d+)\s+(?:phreatomagmatic|phreatic|magmatic)?\s*eruptions?',
        r'(\d+)\s+(?:minor|major|small)?\s*(?:phreatomagmatic|phreatic|magmatic)\s+eruptions?',
        r'(\d+)\s+eruption\s+events?'  # Only capture "eruption event", not just "event"
    ]
    
    count_matches = []
    for pattern in eruption_patterns:
        matches = re.findall(pattern, text)
        count_matches.extend(matches)
    
    result["Eruption_Count"] = sum(map(int, count_matches)) if count_matches else 0

    # FIXED: Enhanced type determination with better severity scoring
    if "phreatomagmatic" in text:
        #result["Eruption_Type"] = "phreatomagmatic"
        result["Eruption_Severity_Score"] = 2  # Higher severity for phreatomagmatic
    elif "phreatic" in text:
        #result["Eruption_Type"] = "phreatic"
        result["Eruption_Severity_Score"] = 1  # Lower severity for phreatic
    elif "magmatic" in text:
        #result["Eruption_Type"] = "magmatic"
        result["Eruption_Severity_Score"] = 3  # Highest severity for magmatic
    elif result["Eruption_Count"] > 0:
        #result["Eruption_Type"] = "other"
        result["Eruption_Severity_Score"] = 1

    # FIXED: Adjust severity based on descriptive keywords
    if "minor" in text or "small" in text:
        result["Eruption_Severity_Score"] = max(0, result["Eruption_Severity_Score"] - 0.5)
    elif "major" in text or "large" in text:
        result["Eruption_Severity_Score"] += 1

    # FIXED: Better duration extraction to avoid overlaps
    total_duration = 0
    duration_instances = 0
    
    # Create a copy of text to modify as we process
    text_for_duration = text

    # 1. Handle ranges like "2–5 minutes" first and remove them from text
    range_durations = re.findall(r'(\d+)\s*[-–]\s*(\d+)\s*minutes?', text_for_duration)
    for low, high in range_durations:
        avg = (int(low) + int(high)) / 2
        total_duration += avg
        duration_instances += 1
    
    # Remove processed ranges from text
    text_for_duration = re.sub(r'\d+\s*[-–]\s*\d+\s*minutes?', '', text_for_duration)

    # 2. Handle exact minute durations from remaining text
    minute_durations = re.findall(r'(\d+)\s*minutes?', text_for_duration)
    total_duration += sum(map(int, minute_durations))
    duration_instances += len(minute_durations)

    # 3. Handle seconds (convert to minutes) - process original text
    second_matches = re.findall(r'(\d+)\s*seconds?', text)
    total_seconds = sum(map(int, second_matches))
    total_duration += total_seconds / 60
    if second_matches:
        duration_instances += 1

    # Final results
    result["Total_Eruption_Duration_Min"] = round(total_duration, 2)
    result["Avg_Eruption_Duration_Min"] = round(total_duration / duration_instances, 2) if duration_instances else 0

    return pd.Series(result)


# --- FIXED: Seismicity: Count total earthquakes + tremors ---
def parse_seismicity_advanced(text):
    text = str(text).lower()
    
    # Initialize defaults
    result = {
        "Volcanic_Earthquakes": 0,
        "Volcanic_Tremors": 0,
        "Total_Tremor_Duration_Min": 0,
        "Has_Long_Tremor": 0,
        "Has_Weak_Tremor": 0,
    }

    # Parse volcanic earthquakes
    match_eq = re.search(r'(\d+)\s+volcanic earthquakes?', text)
    if match_eq:
        result["Volcanic_Earthquakes"] = int(match_eq.group(1))

    # Parse volcanic tremors
    match_tremor = re.search(r'(\d+)\s+volcanic tremors?', text)
    if match_tremor:
        result["Volcanic_Tremors"] = int(match_tremor.group(1))

    # FIXED: Avoid double-counting durations
    text_for_duration = text
    
    # Extract duration ranges like "3-608 minutes long" first
    durations = re.findall(r'(\d+)\s*[-–]\s*(\d+)\s*minutes', text_for_duration)
    for low_str, high_str in durations:
        low, high = int(low_str), int(high_str)
        avg = (low + high) / 2
        result["Total_Tremor_Duration_Min"] += avg
        if high > 60:
            result["Has_Long_Tremor"] = 1
    
    # Remove processed ranges
    text_for_duration = re.sub(r'\d+\s*[-–]\s*\d+\s*minutes', '', text_for_duration)

    # Extract individual tremor durations from remaining text
    solo_durations = re.findall(r'(\d+)\s*minutes', text_for_duration)
    for d in solo_durations:
        minutes = int(d)
        result["Total_Tremor_Duration_Min"] += minutes
        if minutes > 60:
            result["Has_Long_Tremor"] = 1

    # Weak tremor check
    if 'weak volcanic tremor' in text:
        result["Has_Weak_Tremor"] = 1

    return pd.Series(result)


# --- Acidity: Extract numeric acidity (pH) ---
def parse_acidity(val):
    if pd.isna(val) or str(val).strip() == '0':
        return None
    try:
        return float(re.search(r"[\d.]+", str(val)).group())
    except:
        return None

df["Acidity_pH"] = df["Acidity"].apply(parse_acidity)

# --- Temperature: Extract value in Celsius ---
def parse_temperature(val):
    if pd.isna(val) or str(val).strip() == '0':
        return None
    try:
        return float(re.search(r"([\d.]+)\s*℃", str(val)).group(1))
    except:
        return None

df["Crater_Temperature_C"] = df["Temperature"].apply(parse_temperature)

# --- FIXED: Sulfur Dioxide Flux (SO2) ---
def parse_so2(val):
    if pd.isna(val) or 'below detection limit' in str(val).lower():
        return 0
    try:
        # FIXED: Handle commas properly
        val_str = str(val)
        match = re.search(r"([\d,]+)\s*tonnes\s*/\s*day", val_str)
        if match:
            return int(match.group(1).replace(",", ""))
        return None
    except:
        return None

df["SO2_Flux_tpd"] = df["Sulfur_Dioxide_Flux"].apply(parse_so2)

# --- Plume: Estimate plume height and strength ---
def parse_plume_height(val):
    if pd.isna(val) or str(val).strip() == '0':
        return 0
    try:
        match = re.search(r"([\d,]+)\s*meters", str(val).replace(",", ""))
        return int(match.group(1)) if match else 0
    except:
        return 0

def parse_plume_drift(val):
    val = str(val).lower()
    directions = ["north", "northeast", "northwest", "east", "southeast", "south", "southwest", "west"]
    for d in directions:
        if d in val:
            return d
    return "none"

def parse_plume_strength(val):
    val = str(val).lower()
    if "voluminous" in val:
        return 3
    elif "moderate" in val:
        return 2
    elif "weak" in val:
        return 1
    return 0

df["Plume_Height_m"] = df["Plume"].apply(parse_plume_height)
df["Plume_Drift_Direction"] = df["Plume"].apply(parse_plume_drift)
df["Plume_Strength"] = df["Plume"].apply(parse_plume_strength)

# --- FIXED: Ground Deformation: Advanced NLP-style Parsing ---
def analyze_ground_deformation(text):
    text = str(text).lower()
    result = {
        'Caldera_Trend': 0,
        'TVI_Trend': 0,
        'North_Trend': 0,
        'SE_Trend': 0,
        'LT_Inflation': 0,
        'LT_Deflation': 0,
        'ST_Inflation': 0,
        'ST_Deflation': 0
    }

    # FIXED: Better logic to handle both inflation and deflation
    # Check for specific patterns rather than overwriting
    
    # Caldera trends
    if 'caldera' in text:
        if 'long-term deflation of the taal caldera' in text or 'caldera deflation' in text:
            result['Caldera_Trend'] = -1
        elif 'long-term inflation of the taal caldera' in text or 'caldera inflation' in text:
            result['Caldera_Trend'] = 1
        elif 'deflation' in text and 'caldera' in text:
            result['Caldera_Trend'] = -1
        elif 'inflation' in text and 'caldera' in text:
            result['Caldera_Trend'] = 1

    # TVI trends
    if 'tvi' in text or 'taal volcano island' in text:
        if 'deflation' in text:
            result['TVI_Trend'] = -1
        elif 'inflation' in text:
            result['TVI_Trend'] = 1

    # Regional trends
    if 'northern flank' in text or ('north' in text and 'flank' in text):
        if 'inflation' in text:
            result['North_Trend'] = 1
        elif 'deflation' in text:
            result['North_Trend'] = -1

    if 'southeastern flank' in text or ('southeastern' in text and 'flank' in text):
        if 'inflation' in text:
            result['SE_Trend'] = 1
        elif 'deflation' in text:
            result['SE_Trend'] = -1

    # Temporal trends
    if 'long-term inflation' in text:
        result['LT_Inflation'] = 1
    if 'long-term deflation' in text:
        result['LT_Deflation'] = 1
    if 'short-term inflation' in text:
        result['ST_Inflation'] = 1
    if 'short-term deflation' in text:
        result['ST_Deflation'] = 1

    return pd.Series(result)


# --- Apply eruption analysis ---
eruption_features = df["Eruption"].apply(parse_eruption_info)
df = pd.concat([df, eruption_features], axis=1)

# --- Apply seismicity analysis ---
seismicity_features = df["Seismicity"].apply(parse_seismicity_advanced)
df = pd.concat([df, seismicity_features], axis=1)

# Apply deformation analysis
deformation_features = df["Ground_Deformation"].apply(analyze_ground_deformation)
df = pd.concat([df, deformation_features], axis=1)

# --- Drop original non-numeric columns not needed for modeling ---
cols_to_drop = [
    "Eruption",
    "Seismicity",
    "Acidity",
    "Temperature",
    "Sulfur_Dioxide_Flux",
    "Plume",
    "Ground_Deformation",
    "Iframe_Source"
]

df_cleaned = df.drop(columns=cols_to_drop)

# --- Save the cleaned dataset ---
df_cleaned.to_csv('taal_cleaned_forecast_ready.csv', index=False)
print(f"Cleaned dataset saved with {len(df_cleaned)} rows and {len(df_cleaned.columns)} columns.")
print("\nColumn names:")
print(df_cleaned.columns.tolist())