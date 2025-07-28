import pandas as pd
import numpy as np
from datetime import datetime

def reverse_forecast_to_bulletin(forecast_csv_path, output_csv_path):
    """Reverse engineer forecast data back to bulletin format"""
    
    # Load forecast data
    df = pd.read_csv(forecast_csv_path)
    
    # Initialize bulletin format DataFrame
    bulletin_df = pd.DataFrame()
    
    # Convert Date format
    df['Date'] = pd.to_datetime(df['Date'])
    bulletin_df['Date'] = df['Date'].dt.strftime('%d %B %Y')
    
    # Reverse Alert_Level (round to nearest integer)
    bulletin_df['Alert_Level'] = df['Alert_Level'].round().astype(int)
    
    # Reverse Eruption data
    def reverse_eruption(row):
        count = max(0, round(row['Eruption_Count']))
        severity = row['Eruption_Severity_Score']
        
        if count == 0:
            return "0"
        
        # Determine eruption type based on severity
        if severity >= 2.5:
            eruption_type = "Phreatomagmatic"
        elif severity >= 1.5:
            eruption_type = "Phreatic"
        elif severity >= 0.5:
            eruption_type = "Minor Phreatic"
        else:
            eruption_type = "Phreatic"
        
        # Add descriptive terms based on severity
        if severity < 1:
            descriptor = "Minor "
        elif severity > 2:
            descriptor = "Major "
        else:
            descriptor = ""
        
        if count == 1:
            return f"{count} {descriptor}{eruption_type} Eruption event"
        else:
            return f"{count} {descriptor}{eruption_type} Eruption events"
    
    bulletin_df['Eruption'] = df.apply(reverse_eruption, axis=1)
    
    # Reverse Seismicity data
    def reverse_seismicity(row):
        earthquakes = max(0, round(row['Volcanic_Earthquakes']))
        tremors = max(0, round(row['Volcanic_Tremors']))
        
        if earthquakes == 0 and tremors == 0:
            return "0 volcanic earthquakes"
        
        parts = []
        if earthquakes > 0:
            if earthquakes == 1:
                parts.append(f"{earthquakes} volcanic earthquake")
            else:
                parts.append(f"{earthquakes} volcanic earthquakes")
        
        if tremors > 0:
            if tremors == 1:
                parts.append(f"{tremors} volcanic tremor")
            else:
                parts.append(f"{tremors} volcanic tremors")
        
        if earthquakes == 0 and tremors > 0:
            return " including ".join(parts)
        elif earthquakes > 0 and tremors > 0:
            return parts[0] + " including " + parts[1]
        else:
            return parts[0]
    
    bulletin_df['Seismicity'] = df.apply(reverse_seismicity, axis=1)
    
    # Reverse Acidity (add date reference)
    def reverse_acidity(ph_value):
        if pd.isna(ph_value) or ph_value == 0:
            return "0"
        return f"{ph_value:.1f} (19 February 2025)"
    
    bulletin_df['Acidity'] = df['Alert_Level'].apply(lambda x: reverse_acidity(0.3 if x >= 0.5 else 0.2))
    
    # Reverse Temperature (add date reference)
    def reverse_temperature(alert_level):
        # Base temperature varies with alert level
        if alert_level >= 1:
            temp = 68.7
        else:
            temp = 70.7 + np.random.normal(0, 1)  # Add slight variation
        return f"{temp:.1f} ℃ (15 April 2025)"
    
    bulletin_df['Temperature'] = df['Alert_Level'].apply(reverse_temperature)
    
    # Reverse Sulfur Dioxide Flux
    def reverse_so2(flux_value):
        if flux_value <= 0:
            return "Below detection limit"
        flux_int = max(1, round(flux_value))
        return f"{flux_int:,} tonnes / day (15 July 2025)"
    
    bulletin_df['Sulfur_Dioxide_Flux'] = df['SO2_Flux_tpd'].apply(reverse_so2)
    
    # Reverse Plume data
    def reverse_plume(row):
        height = max(0, round(row['Plume_Height_m']))
        strength = round(row['Plume_Strength'])
        
        parts = []
        
        # Add height if > 0
        if height > 0:
            parts.append(f"{height} meters tall")
        
        # Add strength description
        if strength >= 3:
            parts.append("Voluminous emission")
        elif strength >= 2:
            parts.append("Moderate emission")
        elif strength >= 1:
            parts.append("Weak emission")
        
        # Add drift direction (simplified)
        directions = ["northeast", "north", "south", "east", "west", "southeast", "southwest", "northwest"]
        drift = np.random.choice(directions)  # Random direction for forecast
        if parts:
            parts.append(f"{drift} drift")
        
        if not parts:
            return "None observed"
        
        return "; ".join(parts)
    
    bulletin_df['Plume'] = df.apply(reverse_plume, axis=1)
    
    # Reverse Ground Deformation (simplified)
    def reverse_ground_deformation(alert_level):
        # Standard pattern based on alert level
        if alert_level >= 1:
            return "Long-term deflation of the Taal Caldera; short-term inflation of the southeastern flank of the Taal Volcano Island"
        else:
            return "Long-term inflation of the Taal Caldera; short-term deflation of the southeastern flank of the Taal Volcano Island"
    
    bulletin_df['Ground_Deformation'] = df['Alert_Level'].apply(reverse_ground_deformation)
    
    # Save to CSV
    bulletin_df.to_csv(output_csv_path, index=False)
    print(f"Reverse engineered bulletin saved to {output_csv_path}")
    print(f"Generated {len(bulletin_df)} rows with {len(bulletin_df.columns)} columns")
    
    return bulletin_df

# Enhanced version with more realistic data generation
def reverse_forecast_to_bulletin_enhanced(forecast_csv_path, output_csv_path):
    """Enhanced reverse engineering with more realistic bulletin text generation"""
    
    df = pd.read_csv(forecast_csv_path)
    bulletin_df = pd.DataFrame()
    
    # Convert Date format
    df['Date'] = pd.to_datetime(df['Date'])
    bulletin_df['Date'] = df['Date'].dt.strftime('%d %B %Y')
    
    # Alert Level (round to nearest integer, clamp to 1-5)
    bulletin_df['Alert_Level'] = np.clip(df['Alert_Level'].round().astype(int), 1, 5)
    
    # Enhanced Eruption reverse engineering
    def reverse_eruption_enhanced(row):
        count = max(0, round(row['Eruption_Count']))
        severity = row['Eruption_Severity_Score']
        
        if count == 0:
            return "0"
        
        # More nuanced eruption type determination
        if severity >= 2.0:
            eruption_type = "Phreatomagmatic"
            duration = round(10 + severity * 5)  # 15-25 minutes
        elif severity >= 1.0:
            eruption_type = "Phreatic"
            duration = round(3 + severity * 3)  # 6-9 minutes
        else:
            eruption_type = "Minor Phreatic"
            duration = round(2 + severity * 2)  # 2-4 minutes
        
        if count == 1:
            return f"{count} {eruption_type} Eruption event ({duration} minutes long)"
        else:
            return f"{count} {eruption_type} Eruption events"
    
    bulletin_df['Eruption'] = df.apply(reverse_eruption_enhanced, axis=1)
    
    # Enhanced Seismicity with duration details
    def reverse_seismicity_enhanced(row):
        earthquakes = max(0, round(row['Volcanic_Earthquakes']))
        tremors = max(0, round(row['Volcanic_Tremors']))
        
        if earthquakes == 0 and tremors == 0:
            return "0 volcanic earthquakes"
        
        parts = []
        if earthquakes > 0:
            parts.append(f"{earthquakes} volcanic earthquake{'s' if earthquakes > 1 else ''}")
        
        if tremors > 0:
            # Generate realistic tremor durations
            if tremors == 1:
                duration = round(2 + np.random.exponential(3))
                tremor_text = f"{tremors} volcanic tremor ({duration} minutes long)"
            else:
                min_dur = round(2 + np.random.exponential(2))
                max_dur = round(min_dur + np.random.exponential(5))
                tremor_text = f"{tremors} volcanic tremors ({min_dur}-{max_dur} minutes long)"
            
            if earthquakes > 0:
                parts.append(f"including {tremor_text}")
            else:
                parts.append(tremor_text)
        
        return " ".join(parts)
    
    bulletin_df['Seismicity'] = df.apply(reverse_seismicity_enhanced, axis=1)
    
    # Realistic Acidity with date variation
    def reverse_acidity_enhanced(alert_level):
        base_ph = 0.2 + (alert_level - 0.5) * 0.1  # pH varies with alert level
        ph = max(0.1, min(1.0, base_ph + np.random.normal(0, 0.05)))
        return f"{ph:.1f} (19 February 2025)"
    
    bulletin_df['Acidity'] = df['Alert_Level'].apply(reverse_acidity_enhanced)
    
    # Temperature with realistic variation
    def reverse_temperature_enhanced(alert_level):
        base_temp = 68.0 + alert_level * 2  # Temperature correlates with alert level
        temp = base_temp + np.random.normal(0, 1.5)
        return f"{temp:.1f} ℃ (15 April 2025)"
    
    bulletin_df['Temperature'] = df['Alert_Level'].apply(reverse_temperature_enhanced)
    
    # SO2 with realistic formatting
    bulletin_df['Sulfur_Dioxide_Flux'] = df['SO2_Flux_tpd'].apply(
        lambda x: f"{max(1, round(x)):,} tonnes / day (15 July 2025)" if x > 0 else "Below detection limit"
    )
    
    # Enhanced Plume with realistic combinations
    def reverse_plume_enhanced(row):
        height = max(0, round(row['Plume_Height_m']))
        strength = round(row['Plume_Strength'])
        
        parts = []
        
        if height > 0:
            parts.append(f"{height} meters tall")
        
        # Strength descriptions
        strength_map = {0: None, 1: "Weak emission", 2: "Moderate emission", 3: "Voluminous emission"}
        if strength in strength_map and strength_map[strength]:
            parts.append(strength_map[strength])
        
        # Realistic drift patterns
        directions = ["northeast", "north", "south", "east", "west", "southeast", "southwest", "northwest"]
        drift = np.random.choice(directions)
        if parts:
            parts.append(f"{drift} drift")
        
        return "; ".join(parts) if parts else "None observed"
    
    bulletin_df['Plume'] = df.apply(reverse_plume_enhanced, axis=1)
    
    # Ground deformation based on alert patterns
    bulletin_df['Ground_Deformation'] = df['Alert_Level'].apply(
        lambda x: "Long-term deflation of the Taal Caldera; short-term inflation of the southeastern flank of the Taal Volcano Island"
    )
    
    # Save results
    bulletin_df.to_csv(output_csv_path, index=False)
    print(f"Enhanced reverse engineered bulletin saved to {output_csv_path}")
    print(f"Generated {len(bulletin_df)} rows with {len(bulletin_df.columns)} columns")
    
    return bulletin_df

if __name__ == "__main__":
    # Run the reverse engineering
    forecast_path = 'volcano_7day_forecast.csv'
    output_path = 'reversed_bulletin_forecast.csv'
    
    print("Running enhanced reverse engineering...")
    result_df = reverse_forecast_to_bulletin_enhanced(forecast_path, output_path)
    
    print("\nSample of reversed bulletin data:")
    print(result_df.head(3).to_string(index=False))
    
    print("\nColumn structure matches original bulletin format:")
    print(result_df.columns.tolist())