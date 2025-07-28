from flask import Flask, request, jsonify, render_template
import requests
import re
from bs4 import BeautifulSoup
import json
from datetime import datetime
import csv
import os
import time

app = Flask(__name__)

# parse_volcanic_data function is defined later in the file

@app.route('/')
def home():
    return render_template('index.html')

@app.route('/download_html', methods=['POST'])
def download_html():
    """Generate an HTML file with scraped data formatted like the web UI"""
    try:
        data = request.json.get('data')
        if not data:
            return jsonify({'error': 'No data provided'}), 400
        
        # Generate HTML content with the same styling as the web UI
        html_content = generate_html_report(data)
        
        return jsonify({
            'success': True,
            'html_content': html_content,
            'filename': f'scraped_report_{datetime.now().strftime("%Y%m%d_%H%M%S")}.html'
        })
        
    except Exception as e:
        return jsonify({'error': f'Failed to generate HTML report: {str(e)}'}), 500

@app.route('/scrape', methods=['POST'])
def scrape():
    try:
        url = request.json.get('url')
        deep_scrape = request.json.get('deep_scrape', False)
        
        if not url:
            return jsonify({'error': 'URL is required'}), 400
        
        # Try with SSL verification first, fallback to unverified if needed
        try:
            response = requests.get(url, verify=True, timeout=10)
            response.raise_for_status()
        except requests.exceptions.SSLError:
            # If SSL verification fails, try without verification (less secure)
            response = requests.get(url, verify=False, timeout=10)
            response.raise_for_status()
        except requests.exceptions.RequestException as e:
            return jsonify({'error': f'Failed to fetch URL: {str(e)}'}), 400
            
        soup = BeautifulSoup(response.text, 'html.parser')
        title = soup.title.string if soup.title else 'No title found'
        base_url = '/'.join(url.split('/')[:3])  # Get base URL (scheme://domain)
        
        # Extract all hyperlinks that match the Taal Volcano bulletin pattern
        links = []
        
        # Look for links containing the specific text pattern
        for a_tag in soup.find_all('a', href=True):
            href = a_tag['href']
            text = a_tag.get_text(strip=True)
            
            # Normalize text to lowercase for case-insensitive comparison
            normalized_text = text.lower()
            # Check if all required terms are in the normalized text
            if all(term in normalized_text for term in ['taal', 'volcano', 'summary', '24hr', 'observation']):
                # Convert relative URLs to absolute URLs
                if href.startswith('http'):
                    full_url = href
                else:
                    full_url = base_url + ('/' if not href.startswith('/') else '') + href
                
                links.append({
                    'url': full_url,
                    'text': text,
                    'is_external': not href.startswith(('http', '//')) or base_url not in href
                })
        
        # Check if this is a deep scrape request to find iframe content
        iframe_content = None
        iframe_src = None
        
        if deep_scrape:
            # Look for the specific div class and iframe
            # First try to find the exact class match
            target_div = soup.find('div', class_='sppb-addon-content')
            
            # If not found, try a more flexible approach with partial class match
            if not target_div:
                for div in soup.find_all('div'):
                    if div.has_attr('class') and 'sppb-addon-content' in div.get('class'):
                        target_div = div
                        break
            
            # If still not found, look for any div that might contain 'sppb' and 'content' in its class
            if not target_div:
                for div in soup.find_all('div'):
                    if div.has_attr('class') and any('sppb' in cls and 'content' in cls for cls in div.get('class')):
                        target_div = div
                        break
            
            # If we found a target div, look for an iframe inside it
            iframe = None
            if target_div:
                iframe = target_div.find('iframe')
            
            # If no iframe found in the target div, look for any iframe on the page
            if not iframe:
                iframe = soup.find('iframe')
                
            if iframe and iframe.has_attr('src'):
                iframe_src = iframe['src']
                    
                    # Make sure iframe src is an absolute URL
                if not iframe_src.startswith(('http://', 'https://')):
                        iframe_src = base_url + ('/' if not iframe_src.startswith('/') else '') + iframe_src
                    
                try:
                        # Fetch the content from the iframe source
                        iframe_response = requests.get(iframe_src, verify=False, timeout=10)
                        iframe_response.raise_for_status()
                        iframe_soup = BeautifulSoup(iframe_response.text, 'html.parser')
                        
                        # NEW: Extract Alert Level from row-one div structure
                        alert_level_data = extract_alert_level_from_row_one(iframe_soup)
                        
                        # Extract all images from the iframe content
                        images = []
                        for img in iframe_soup.find_all('img'):
                            if img.has_attr('src'):
                                img_src = img['src']
                                if not img_src.startswith(('http://', 'https://')):
                                    img_base = '/'.join(iframe_src.split('/')[:3])
                                    img_src = img_base + ('/' if not img_src.startswith('/') else '') + img_src
                                images.append(img_src)
                        
                        # Extract text content from iframe
                        text_content = iframe_soup.get_text(separator='\n', strip=True)
                        
                        # Parse volcanic data from the text content
                        volcanic_data = parse_volcanic_data(text_content)
                        
                        # Merge alert level data with volcanic data
                        if alert_level_data:
                            volcanic_data.update(alert_level_data)
                        
                        iframe_content = {
                            'url': iframe_src,
                            'content': iframe_response.text,
                            'images': images,
                            'text': text_content,
                            'volcanic_data': volcanic_data
                        }
                except requests.exceptions.RequestException as e:
                        iframe_content = {
                            'url': iframe_src,
                            'error': f'Failed to fetch iframe content: {str(e)}'
                        }
        
        # Parse volcanic data from the main page content
        main_page_text = soup.get_text(separator='\n', strip=True)
        main_page_volcanic_data = parse_volcanic_data(main_page_text)
        
        # Get full page content for JSON download
        full_content = {
            'url': url,
            'title': title,
            'links': links,
            'total_links': len(links),
            'content': response.text,
            'main_page_volcanic_data': main_page_volcanic_data,
            'metadata': {
                'status': response.status_code,
                'content_type': response.headers.get('content-type', ''),
                'content_length': len(response.content) if response.content else 0,
                'encoding': response.encoding
            }
        }
        
        # Add iframe content if found
        if iframe_content:
            full_content['iframe_content'] = iframe_content
            full_content['iframe_src'] = iframe_src
        
        return jsonify({
            'success': True,
            'data': {
                'title': title,
                'links': links,
                'total_links': len(links),
                'iframe_content': iframe_content,
                'iframe_src': iframe_src,
                'volcanic_data': main_page_volcanic_data,  # Include volcanic data in the main response
                'full_content': full_content
            }
        })
        
    except requests.exceptions.RequestException as e:
        return jsonify({'error': str(e)}), 500
    except Exception as e:
        return jsonify({'error': 'An error occurred during scraping'}), 500

def generate_html_report(data):
    """Generate an HTML report with the same styling as the web UI.
    
    Args:
        data (dict): The scraped data to display
        
    Returns:
        str: HTML content as a string
    """
    # Start with HTML boilerplate and include Bootstrap CSS
    html = f'''
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Scraped Data Report - {data.get('title', 'No Title')}</title>
        <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
        <style>
            body {{
                background-color: #f8f9fa;
                min-height: 100vh;
                padding: 20px;
            }}
            .container {{
                max-width: 900px;
                margin: 0 auto;
            }}
            .card {{
                box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
                margin-bottom: 20px;
            }}
            .link-item {{
                padding: 12px 15px;
                border-bottom: 1px solid #eee;
                word-break: break-all;
            }}
            .link-item:last-child {{
                border-bottom: none;
            }}
            .link-text {{
                color: #333;
                font-weight: 500;
                margin-bottom: 4px;
                display: block;
            }}
            .link-url {{
                color: #0a58ca;
                font-size: 0.85em;
                display: block;
            }}
            .external-badge {{
                font-size: 0.7em;
                vertical-align: middle;
                margin-left: 8px;
            }}
            .no-links {{
                color: #6c757d;
                text-align: center;
                padding: 30px 0;
            }}
            pre {{  
                background-color: #f8f9fa;
                padding: 15px;
                border-radius: 5px;
                overflow-x: auto;
            }}
            img {{  
                max-width: 100%;
                height: auto;
            }}
            .timestamp {{  
                color: #6c757d;
                font-size: 0.8em;
                text-align: right;
                margin-top: 5px;
            }}
        </style>
    </head>
    <body>
        <div class="container">
            <h1 class="mb-4 text-center">Scraped Data Report</h1>
            <div class="timestamp">Generated on: {datetime.now().strftime("%Y-%m-%d %H:%M:%S")}</div>
    '''
    
    # Add source URL and title section
    html += f'''
            <div class="card mb-4">
                <div class="card-header bg-primary text-white">
                    <h5 class="mb-0">Source Information</h5>
                </div>
                <div class="card-body">
                    <h5>{data.get('title', 'No Title')}</h5>
                    <a href="{data.get('url', '#')}" target="_blank">{data.get('url', 'No URL')}</a>
                </div>
            </div>
    '''
    
    # Add volcanic data section if available
    volcanic_data = data.get('volcanic_data', {})
    if volcanic_data and any(volcanic_data.values()):
        html += '''
            <div class="card mb-4">
                <div class="card-header bg-primary text-white">
                    <h5 class="mb-0">Volcanic Activity Data</h5>
                </div>
                <div class="card-body">
                    <div class="row">
        '''
        
        # Add Alert Level if available
        if volcanic_data.get('alert_level'):
            alert_level = volcanic_data['alert_level']
            alert_color = get_alert_level_color(alert_level)
            html += f'''
                <div class="col-md-4 mb-2">
                    <div class="card bg-{alert_color} text-white">
                        <div class="card-body p-2 text-center">
                            <h5 class="card-title mb-0">Alert Level</h5>
                            <p class="display-4 mb-0">{alert_level}</p>
                        </div>
                    </div>
                </div>
            '''
        
        # Add Volcanic Earthquakes if available
        if volcanic_data.get('volcanic_earthquakes'):
            html += f'''
                <div class="col-md-4 mb-2">
                    <div class="card border-info">
                        <div class="card-body p-2 text-center">
                            <h6 class="card-title">Volcanic Earthquakes</h6>
                            <p class="h4 mb-0">{volcanic_data['volcanic_earthquakes']}</p>
                        </div>
                    </div>
                </div>
            '''
        
        # Add Gas Emissions if available
        if volcanic_data.get('gas_emissions'):
            html += f'''
                <div class="col-md-4 mb-2">
                    <div class="card border-warning">
                        <div class="card-body p-2 text-center">
                            <h6 class="card-title">Gas Emissions</h6>
                            <p class="h4 mb-0">{volcanic_data['gas_emissions']}</p>
                        </div>
                    </div>
                </div>
            '''
        
        html += '</div>' # Close row
        
        # Add second row for additional data
        if volcanic_data.get('plume_activity') or volcanic_data.get('ground_deformation'):
            html += '<div class="row mt-2">'
            
            # Add Plume Activity if available
            if volcanic_data.get('plume_activity'):
                html += f'''
                    <div class="col-md-6 mb-2">
                        <div class="card border-secondary">
                            <div class="card-header py-1 px-3">Plume Activity</div>
                            <div class="card-body py-2 px-3">
                                <p class="card-text mb-0">{volcanic_data['plume_activity']}</p>
                            </div>
                        </div>
                    </div>
                '''
            
            # Add Ground Deformation if available
            if volcanic_data.get('ground_deformation'):
                html += f'''
                    <div class="col-md-6 mb-2">
                        <div class="card border-secondary">
                            <div class="card-header py-1 px-3">Ground Deformation</div>
                            <div class="card-body py-2 px-3">
                                <p class="card-text mb-0">{volcanic_data['ground_deformation']}</p>
                            </div>
                        </div>
                    </div>
                '''
            
            html += '</div>' # Close row
        
        # Add Seismic Activity if available
        if volcanic_data.get('seismic_activity'):
            html += f'''
                <div class="row mt-2">
                    <div class="col-12 mb-2">
                        <div class="card border-secondary">
                            <div class="card-header py-1 px-3">Seismic Activity</div>
                            <div class="card-body py-2 px-3">
                                <p class="card-text mb-0">{volcanic_data['seismic_activity']}</p>
                            </div>
                        </div>
                    </div>
                </div>
            '''
        
        # Add Observations if available
        if volcanic_data.get('observations'):
            observations = volcanic_data['observations']
            html += '''
                <div class="mt-3">
                    <h6>Key Observations:</h6>
                    <ul class="list-group">
            '''
            
            if isinstance(observations, list):
                for obs in observations:
                    html += f'<li class="list-group-item">{obs}</li>'
            else:
                html += f'<li class="list-group-item">{observations}</li>'
            
            html += '''
                    </ul>
                </div>
            '''
        
        html += '''
                </div>
            </div>
        '''
    
    # Add links section if available
    links = data.get('links', [])
    if links:
        html += f'''
            <div class="card mb-4">
                <div class="card-header bg-primary text-white d-flex justify-content-between align-items-center">
                    <h5 class="mb-0">Links Found ({len(links)})</h5>
                </div>
                <div class="list-group list-group-flush">
        '''
        
        for link in links:
            html += f'''
                <div class="link-item">
                    <span class="link-text">{link.get('text', 'No text')}</span>
                    <a href="{link.get('url', '#')}" class="link-url" target="_blank">{link.get('url', 'No URL')}</a>
                    {f'<span class="badge bg-secondary external-badge">External</span>' if link.get('is_external') else ''}
                </div>
            '''
        
        html += '''
                </div>
            </div>
        '''
    else:
        html += '''
            <div class="card mb-4">
                <div class="card-body">
                    <div class="no-links">No links found on this page.</div>
                </div>
            </div>
        '''
    
    # Add iframe content section if available
    iframe_content = data.get('iframe_content', {})
    if iframe_content:
        html += '''
            <div class="card mb-4">
                <div class="card-header bg-primary text-white">
                    <h5 class="mb-0">Iframe Content</h5>
                </div>
                <div class="card-body">
        '''
        
        # Add iframe source
        iframe_src = data.get('iframe_src', '')
        if iframe_src:
            html += f'''
                <div class="mb-3">
                    <h6>Iframe Source:</h6>
                    <div class="input-group mb-3">
                        <input type="text" class="form-control" value="{iframe_src}" readonly>
                    </div>
                </div>
            '''
        
        # Add images if available
        images = iframe_content.get('images', [])
        if images:
            html += f'''
                <h6>Images ({len(images)}):</h6>
                <div class="row">
            '''
            
            for img_src in images:
                html += f'''
                    <div class="col-md-4 mb-3">
                        <div class="card">
                            <img src="{img_src}" class="card-img-top" alt="Image" onerror="this.onerror=null;this.src='https://via.placeholder.com/150x100?text=Image+Error';">
                            <div class="card-body p-2 text-center">
                                <small class="text-muted">{img_src}</small>
                            </div>
                        </div>
                    </div>
                '''
            
            html += '''
                </div>
            '''
        
        # Add volcanic data from iframe if available
        iframe_volcanic_data = iframe_content.get('volcanic_data', {})
        if iframe_volcanic_data and any(iframe_volcanic_data.values()):
            html += '''
                <div class="mt-4">
                    <h6>Volcanic Activity Data from Iframe:</h6>
                    <div class="card mb-3">
                        <div class="card-body">
                            <div class="row">
            '''
            
            # Add Alert Level if available
            if iframe_volcanic_data.get('alert_level'):
                alert_level = iframe_volcanic_data['alert_level']
                alert_color = get_alert_level_color(alert_level)
                html += f'''
                    <div class="col-md-4 mb-2">
                        <div class="card bg-{alert_color} text-white">
                            <div class="card-body p-2 text-center">
                                <h5 class="card-title mb-0">Alert Level</h5>
                                <p class="display-4 mb-0">{alert_level}</p>
                            </div>
                        </div>
                    </div>
                '''
            
            # Add Volcanic Earthquakes if available
            if iframe_volcanic_data.get('volcanic_earthquakes'):
                html += f'''
                    <div class="col-md-4 mb-2">
                        <div class="card border-info">
                            <div class="card-body p-2 text-center">
                                <h6 class="card-title">Volcanic Earthquakes</h6>
                                <p class="h4 mb-0">{iframe_volcanic_data['volcanic_earthquakes']}</p>
                            </div>
                        </div>
                    </div>
                '''
            
            # Add Gas Emissions if available
            if iframe_volcanic_data.get('gas_emissions'):
                html += f'''
                    <div class="col-md-4 mb-2">
                        <div class="card border-warning">
                            <div class="card-body p-2 text-center">
                                <h6 class="card-title">Gas Emissions</h6>
                                <p class="h4 mb-0">{iframe_volcanic_data['gas_emissions']}</p>
                            </div>
                        </div>
                    </div>
                '''
            
            html += '</div>' # Close row
            
            # Add second row for additional data
            if iframe_volcanic_data.get('plume_activity') or iframe_volcanic_data.get('ground_deformation'):
                html += '<div class="row mt-2">'
                
                # Add Plume Activity if available
                if iframe_volcanic_data.get('plume_activity'):
                    html += f'''
                        <div class="col-md-6 mb-2">
                            <div class="card border-secondary">
                                <div class="card-header py-1 px-3">Plume Activity</div>
                                <div class="card-body py-2 px-3">
                                    <p class="card-text mb-0">{iframe_volcanic_data['plume_activity']}</p>
                                </div>
                            </div>
                        </div>
                    '''
                
                # Add Ground Deformation if available
                if iframe_volcanic_data.get('ground_deformation'):
                    html += f'''
                        <div class="col-md-6 mb-2">
                            <div class="card border-secondary">
                                <div class="card-header py-1 px-3">Ground Deformation</div>
                                <div class="card-body py-2 px-3">
                                    <p class="card-text mb-0">{iframe_volcanic_data['ground_deformation']}</p>
                                </div>
                            </div>
                        </div>
                    '''
                
                html += '</div>' # Close row
            
            # Add Seismic Activity if available
            if iframe_volcanic_data.get('seismic_activity'):
                html += f'''
                    <div class="row mt-2">
                        <div class="col-12 mb-2">
                            <div class="card border-secondary">
                                <div class="card-header py-1 px-3">Seismic Activity</div>
                                <div class="card-body py-2 px-3">
                                    <p class="card-text mb-0">{iframe_volcanic_data['seismic_activity']}</p>
                                </div>
                            </div>
                        </div>
                    </div>
                '''
            
            # Add Observations if available
            if iframe_volcanic_data.get('observations'):
                observations = iframe_volcanic_data['observations']
                html += '''
                    <div class="mt-3">
                        <h6>Key Observations:</h6>
                        <ul class="list-group">
                '''
                
                if isinstance(observations, list):
                    for obs in observations:
                        html += f'<li class="list-group-item">{obs}</li>'
                else:
                    html += f'<li class="list-group-item">{observations}</li>'
                
                html += '''
                        </ul>
                    </div>
                '''
            
            html += '''
                        </div>
                    </div>
                </div>
            '''
        
        # Add text content from iframe
        text_content = iframe_content.get('text', '')
        if text_content:
            html += '''
                <div class="mt-3">
                    <h6>Text Content:</h6>
                    <div class="card">
                        <div class="card-body">
                            <pre class="mb-0">{}</pre>
                        </div>
                    </div>
                </div>
            '''.format(text_content.replace('<', '&lt;').replace('>', '&gt;'))
        
        html += '''
                </div>
            </div>
        '''
    
    # Add raw JSON data section
    html += '''
            <div class="card mb-4">
                <div class="card-header bg-secondary text-white">
                    <h5 class="mb-0">Raw JSON Data</h5>
                </div>
                <div class="card-body">
                    <pre>{}</pre>
                </div>
            </div>
    '''.format(json.dumps(data, indent=2).replace('<', '&lt;').replace('>', '&gt;'))
    
    # Close HTML document
    html += '''
        </div>
        <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    </body>
    </html>
    '''
    
    return html

def get_alert_level_color(alert_level):
    """Determine the appropriate color for an alert level.
    
    Args:
        alert_level (str): The alert level value
        
    Returns:
        str: Bootstrap color class name
    """
    try:
        level = int(alert_level)
        if level == 0:
            return "success"  # Green
        elif level == 1:
            return "info"     # Blue
        elif level == 2:
            return "warning"  # Yellow
        elif level == 3:
            return "orange"   # Orange (custom)
        elif level == 4:
            return "danger"   # Red
        else:
            return "secondary" # Gray
    except (ValueError, TypeError):
        return "secondary"    # Gray for non-numeric values

def parse_volcanic_data(text):
    """Extract volcanic activity data from text content using regex patterns.
    
    Args:
        text (str): The text content to parse
        
    Returns:
        dict: Dictionary containing extracted volcanic data
    """
    if not text:
        return {}
    
    # Initialize result dictionary with default values
    result = {
        'alert_level': None,
        'volcanic_earthquakes': None,
        'gas_emissions': None,
        'plume_activity': None,
        'ground_deformation': None,
        'seismic_activity': None,
        'observations': []
    }
    
    # Convert text to lowercase for case-insensitive matching
    text_lower = text.lower()
    
    # Extract Alert Level (looking for patterns like "Alert Level 2" or "Alert Level: 2")
    alert_level_match = re.search(r'alert\s*level\s*[:\-]?\s*([0-5])', text_lower)
    if alert_level_match:
        result['alert_level'] = int(alert_level_match.group(1))
    
    # Extract volcanic earthquake count
    earthquake_match = re.search(r'([0-9]+)\s*volcanic\s*earthquakes', text_lower)
    if earthquake_match:
        result['volcanic_earthquakes'] = int(earthquake_match.group(1))
    
    # Extract SO2 gas emissions (looking for patterns like "X tonnes/day" or "X tons/day")
    gas_match = re.search(r'([0-9,.]+)\s*(?:tonnes|tons)(?:/|\s+per\s+)day', text_lower)
    if gas_match:
        # Remove commas from number string and convert to float
        gas_value = gas_match.group(1).replace(',', '')
        result['gas_emissions'] = float(gas_value)
    
    # Extract plume activity (height)
    plume_match = re.search(r'plume(?:s)?\s*(?:of\s*)?([0-9,.]+)\s*(?:to\s*[0-9,.]+\s*)?(?:meter|m)\s*(?:high|tall|in height)', text_lower)
    if plume_match:
        result['plume_activity'] = plume_match.group(1) + ' meters'
    
    # Extract ground deformation information
    deformation_patterns = [
        r'(inflation|deflation|uplift|subsidence|bulging).{1,50}(main crater|edifice|volcanic edifice)',
        r'(main crater|volcanic edifice).{1,50}(inflation|deflation|uplift|subsidence|bulging)'
    ]
    
    for pattern in deformation_patterns:
        deformation_match = re.search(pattern, text_lower)
        if deformation_match:
            # Extract the full sentence containing the match
            sentence_pattern = r'[^.!?]*' + re.escape(deformation_match.group(0)) + r'[^.!?]*[.!?]'
            sentence_match = re.search(sentence_pattern, text_lower)
            if sentence_match:
                result['ground_deformation'] = sentence_match.group(0).strip().capitalize()
            break
    
    # Extract general seismic activity information
    seismic_patterns = [
        r'(seismic\s*activity).{1,100}(increased|decreased|remained|stable|unstable)',
        r'(increased|decreased|remained|stable|unstable).{1,100}(seismic\s*activity)'
    ]
    
    for pattern in seismic_patterns:
        seismic_match = re.search(pattern, text_lower)
        if seismic_match:
            # Extract the full sentence containing the match
            sentence_pattern = r'[^.!?]*' + re.escape(seismic_match.group(0)) + r'[^.!?]*[.!?]'
            sentence_match = re.search(sentence_pattern, text_lower)
            if sentence_match:
                result['seismic_activity'] = sentence_match.group(0).strip().capitalize()
            break
    
    # Extract key observations (important sentences containing specific keywords)
    observation_keywords = [
        'phreatic eruption', 'phreatomagmatic eruption', 'magmatic eruption',
        'volcanic tremor', 'harmonic tremor', 'crater glow', 'lava dome',
        'pyroclastic flow', 'pyroclastic density current', 'lahar', 'ashfall',
        'volcanic ash', 'steam emission', 'fissure', 'fissuring', 'crack',
        'crater lake', 'main crater lake', 'sulfur dioxide', 'sulfur emission'
    ]
    
    # Split text into sentences
    sentences = re.split(r'(?<=[.!?])\s+', text)
    
    for sentence in sentences:
        sentence_lower = sentence.lower()
        if any(keyword in sentence_lower for keyword in observation_keywords):
            # Clean up the sentence
            clean_sentence = sentence.strip()
            if clean_sentence and len(clean_sentence) > 10:  # Avoid very short fragments
                result['observations'].append(clean_sentence)
    
    # Limit to top 5 most important observations to avoid overwhelming the UI
    result['observations'] = result['observations'][:5]
    
    return result

@app.route('/bulk_scrape', methods=['POST'])
def bulk_scrape():
    """Bulk scrape Taal Volcano data from paginated URLs and save to CSV"""
    try:
        # Initialize CSV file with headers
        csv_filename = 'taal_volcano_bulletin_data.csv'
        csv_headers = ['Date', 'Alert_Level', 'Eruption', 'Seismicity', 'Acidity', 'Temperature',
                       'Sulfur_Dioxide_Flux', 'Plume', 'Ground_Deformation', 'Iframe_Source']
        
        # Check if CSV exists and get the latest date
        latest_date_in_csv = None
        csv_exists = os.path.exists(csv_filename)
        
        if csv_exists:
            try:
                with open(csv_filename, 'r', encoding='utf-8') as csvfile:
                    reader = csv.reader(csvfile)
                    next(reader)  # Skip header
                    dates = []
                    rows = list(reader)  # Read all rows into memory
                    
                    for row in rows:
                        if row and row[0] != '0':  # Skip empty or placeholder dates
                            try:
                                # Parse date in format "28 July 2025"
                                parsed_date = datetime.strptime(row[0], '%d %B %Y')
                                dates.append(parsed_date)
                            except ValueError:
                                continue
                    
                    if dates:
                        latest_date_in_csv = max(dates)
                        print(f"Latest date in CSV: {latest_date_in_csv.strftime('%d %B %Y')}")
            except Exception as e:
                print(f"Error reading existing CSV: {str(e)}")
                latest_date_in_csv = None
        
        # Get current date
        current_date = datetime.now().date()
        
        # Check if we need to scrape
        if latest_date_in_csv and latest_date_in_csv.date() >= current_date:
            return jsonify({
                'success': True,
                'message': f'Data is up to date. Latest date in CSV: {latest_date_in_csv.strftime("%d %B %Y")}',
                'csv_filename': csv_filename,
                'total_pages_processed': 0,
                'total_data_entries': 0
            })
        
        # Create or prepare CSV file if not exists
        if not csv_exists:
            with open(csv_filename, 'w', newline='', encoding='utf-8') as csvfile:
                writer = csv.writer(csvfile)
                writer.writerow(csv_headers)
        
        total_processed = 0
        total_data_entries = 0
        new_data_rows = []
        
        # Iterate through paginated URLs
        for n in range(0, 3411, 10):  # n=0 to n=3410, increment by 10
            base_url = f"https://www.phivolcs.dost.gov.ph/index.php/volcano-hazard/volcano-bulletin2/taal-volcano?start={n}"
            
            try:
                print(f"Processing page: {base_url}")
                
                # Fetch the page
                response = requests.get(base_url, verify=False, timeout=15)
                response.raise_for_status()
                
                soup = BeautifulSoup(response.text, 'html.parser')
                base_domain = '/'.join(base_url.split('/')[:3])
                
                # Find all Taal Volcano bulletin links
                bulletin_links = []
                for a_tag in soup.find_all('a', href=True):
                    href = a_tag['href']
                    text = a_tag.get_text(strip=True)
                    
                    # Check for Taal Volcano Summary pattern
                    normalized_text = text.lower()
                    if all(term in normalized_text for term in ['taal', 'volcano', 'summary', '24hr', 'observation']):
                        # Convert to absolute URL
                        if href.startswith('http'):
                            full_url = href
                        else:
                            full_url = base_domain + ('/' if not href.startswith('/') else '') + href
                        
                        bulletin_links.append({
                            'url': full_url,
                            'text': text
                        })
                
                print(f"Found {len(bulletin_links)} bulletin links on page {n}")
                
                # Process each bulletin link with deep scraping
                for link in bulletin_links:
                    try:
                        print(f"Deep scraping: {link['url']}")
                        
                        # Extract date from the bulletin text or URL
                        date_extracted = extract_date_from_text(link['text']) or extract_date_from_url(link['url'])
                        
                        # Skip if we already have this date or if it's older than our latest date
                        if date_extracted and latest_date_in_csv:
                            try:
                                extracted_date_obj = datetime.strptime(date_extracted, '%d %B %Y')
                                if extracted_date_obj <= latest_date_in_csv:
                                    print(f"Skipping {date_extracted} - already have newer or equal data")
                                    continue
                            except ValueError:
                                pass  # Continue processing if date parsing fails
                        
                        # Perform deep scraping on the bulletin link
                        link_response = requests.get(link['url'], verify=False, timeout=15)
                        link_response.raise_for_status()
                        
                        link_soup = BeautifulSoup(link_response.text, 'html.parser')
                        link_base_url = '/'.join(link['url'].split('/')[:3])
                        
                        # Find iframe content
                        iframe_src = None
                        iframe_data = None
                        
                        # Look for iframe
                        target_div = link_soup.find('div', class_='sppb-addon-content')
                        if not target_div:
                            for div in link_soup.find_all('div'):
                                if div.has_attr('class') and 'sppb-addon-content' in div.get('class'):
                                    target_div = div
                                    break
                        
                        iframe = None
                        if target_div:
                            iframe = target_div.find('iframe')
                        if not iframe:
                            iframe = link_soup.find('iframe')
                        
                        if iframe and iframe.has_attr('src'):
                            iframe_src = iframe['src']
                            if not iframe_src.startswith(('http://', 'https://')):
                                iframe_src = link_base_url + ('/' if not iframe_src.startswith('/') else '') + iframe_src
                            
                            try:
                                # Fetch iframe content
                                iframe_response = requests.get(iframe_src, verify=False, timeout=15)
                                iframe_response.raise_for_status()
                                iframe_soup = BeautifulSoup(iframe_response.text, 'html.parser')
                                
                                # NEW PARSING METHOD: Look for PARAMETERS section
                                volcanic_data = parse_unified_volcanic_data(iframe_soup)
                                
                                # Prepare CSV row data
                                csv_row = [
                                    date_extracted or '0',
                                    volcanic_data.get('Alert_Level') or '0',
                                    volcanic_data.get('Eruption') or '0',
                                    volcanic_data.get('Seismicity') or '0',
                                    volcanic_data.get('Acidity') or '0',
                                    volcanic_data.get('Temperature') or '0',
                                    volcanic_data.get('Sulfur_Dioxide_Flux') or '0',
                                    volcanic_data.get('Plume') or '0',
                                    volcanic_data.get('Ground_Deformation') or '0',
                                    iframe_src or '0'
                                ]
                                
                                # Append new data row to the list
                                new_data_rows.append(csv_row)
                                
                                total_data_entries += 1
                                print(f"Data saved for: {date_extracted}")
                                
                            except Exception as e:
                                print(f"Error processing iframe {iframe_src}: {str(e)}")
                        
                        # Small delay between requests to be respectful
                        time.sleep(1)
                        
                    except Exception as e:
                        print(f"Error processing bulletin link {link['url']}: {str(e)}")
                        continue
                
                total_processed += 1
                
                # Delay between pages to be respectful to the server
                time.sleep(2)
                
            except Exception as e:
                print(f"Error processing page {base_url}: {str(e)}")
                continue
        
        # Read existing data from the CSV to avoid duplicates
        all_rows = []
        if csv_exists:
            with open(csv_filename, 'r', encoding='utf-8') as csvfile:
                reader = csv.reader(csvfile)
                next(reader)  # Skip header
                all_rows = list(reader)
        
        # Combine existing data with the new data
        all_rows.extend(new_data_rows)
        
        # Sort all rows by the date column (latest to earliest)
        all_rows.sort(key=lambda row: datetime.strptime(row[0], '%d %B %Y'), reverse=True)
        
        # Rewrite the CSV file with updated data
        with open(csv_filename, 'w', newline='', encoding='utf-8') as csvfile:
            writer = csv.writer(csvfile)
            writer.writerow(csv_headers)  # Write headers
            writer.writerows(all_rows)  # Write sorted rows
        
        return jsonify({
            'success': True,
            'message': f'Bulk scraping completed. Processed {total_processed} pages, saved {total_data_entries} data entries.',
            'csv_filename': csv_filename,
            'total_pages_processed': total_processed,
            'total_data_entries': total_data_entries
        })
        
    except Exception as e:
        return jsonify({'error': f'Bulk scraping failed: {str(e)}'}), 500


def parse_parameters_table(soup):
    """Legacy wrapper for backward compatibility"""
    return parse_unified_volcanic_data(soup)

def parse_unified_volcanic_data(soup):
    """Unified method to parse both PARAMETERS section and Alert Level from row-one div
    
    Args:
        soup (BeautifulSoup): The parsed HTML content
        
    Returns:
        dict: Dictionary containing extracted volcanic parameters
    """
    result = {
        'Alert_Level': '0',
        'Eruption': '0',
        'Seismicity': '0',
        'Acidity': '0',
        'Temperature': '0',
        'Sulfur_Dioxide_Flux': '0',
        'Plume': '0',
        'Ground_Deformation': '0'
    }
    
    try:
        # Method 1: Try to extract from PARAMETERS section
        parameters_data = extract_from_parameters_section(soup)
        if parameters_data:
            result.update(parameters_data)
            print("Successfully extracted data from PARAMETERS section")
        
        # Method 2: Try to extract Alert Level from row-one div structure
        # Only if Alert Level wasn't found in PARAMETERS section
        if result['Alert_Level'] == '0':
            alert_data = extract_from_row_one_section(soup)
            if alert_data:
                result.update(alert_data)
                print("Successfully extracted Alert Level from row-one section")
        
        # Method 3: Fallback - search all tables for any missing parameters
        missing_params = [key for key, value in result.items() if value == '0']
        if missing_params:
            fallback_data = extract_from_all_tables(soup, missing_params)
            if fallback_data:
                result.update(fallback_data)
                print("Successfully extracted additional data from fallback method")
    
    except Exception as e:
        print(f"Error in unified volcanic data parsing: {str(e)}")
    
    return result

def extract_from_parameters_section(soup):
    """Extract data from PARAMETERS section using existing logic"""
    result = {}
    
    try:
        # Find the PARAMETERS section
        parameters_header = soup.find('p', class_='title1 bold', string='PARAMETERS')
        if not parameters_header:
            parameters_header = soup.find('p', string=re.compile(r'PARAMETERS', re.IGNORECASE))
        
        if parameters_header:
            table = find_table_after_element(parameters_header, soup)
            if table:
                extracted_data = parse_table_data(table)
                result.update(extracted_data)
    
    except Exception as e:
        print(f"Error extracting from PARAMETERS section: {str(e)}")
    
    return result

def extract_from_row_one_section(soup):
    """Extract Alert Level from row-one div structure using unified logic"""
    result = {}
    
    try:
        # Find the div with class="row-one"
        row_one_div = soup.find('div', class_='row-one')
        
        if row_one_div:
            print("Found row-one div")
            
            # Look for div with class="col-two" within or after row-one
            col_two_div = row_one_div.find('div', class_='col-two')
            
            # If not found within row-one, search more broadly
            if not col_two_div:
                parent = row_one_div.parent
                if parent:
                    col_two_div = parent.find('div', class_='col-two')
                if not col_two_div:
                    col_two_div = soup.find('div', class_='col-two')
            
            if col_two_div:
                print("Found col-two div")
                table = col_two_div.find('table')
                
                if table:
                    print("Found table in col-two")
                    # Use the same table parsing logic as PARAMETERS section
                    extracted_data = parse_table_data(table, focus_on_alert=True)
                    result.update(extracted_data)
    
    except Exception as e:
        print(f"Error extracting from row-one section: {str(e)}")
    
    return result

def find_table_after_element(element, soup):
    """Find table that comes after a specific element"""
    current_element = element
    table = None
    
    # Search for table in the following siblings or parent's siblings
    while current_element:
        if current_element.name == 'table':
            table = current_element
            break
        
        # Check if current element contains a table
        if hasattr(current_element, 'find') and current_element.find('table'):
            table = current_element.find('table')
            break
        
        # Move to next sibling
        current_element = current_element.find_next_sibling()
        if not current_element:
            # If no more siblings, try looking in the parent's next siblings
            parent = element.parent
            if parent:
                current_element = parent.find_next_sibling()
    
    return table

def parse_table_data(table, focus_on_alert=False):
    """Unified table parsing logic for both PARAMETERS and row-one sections"""
    result = {}
    
    try:
        rows = table.find_all('tr')
        
        for row in rows:
            cells = row.find_all(['td', 'th'])
            if len(cells) >= 2:
                first_cell = cells[0]
                second_cell = cells[1]
                
                # Extract parameter name from first cell
                param_name = extract_parameter_name(first_cell)
                
                # Extract data from second cell using unified logic
                if param_name:
                    # Special handling for seismicity to capture description
                    if 'seismic' in param_name.lower():
                        data_value = extract_seismicity_with_description(second_cell)
                    else:
                        data_value = extract_cell_data(second_cell)
                    
                    # Map parameter name to result key
                    param_key, processed_value = map_parameter_to_key(param_name, data_value, focus_on_alert)
                    
                    # Store the extracted data
                    if param_key and processed_value:
                        result[param_key] = processed_value
                        print(f"Extracted {param_key}: {processed_value}")
    
    except Exception as e:
        print(f"Error parsing table data: {str(e)}")
    
    return result

def extract_parameter_name(cell):
    """Extract parameter name from table cell"""
    param_name = None
    
    # Try to find parameter name in bold element
    param_element = cell.find('b')
    if param_element:
        param_name = param_element.get_text(strip=True)
    else:
        # Try to find parameter name in any text within the cell
        cell_text = cell.get_text(strip=True)
        # Look for known parameter names
        for param in ['Alert Level', 'Eruption', 'Seismicity', 'Acidity', 'Temperature', 
                    'Sulfur Dioxide Flux', 'Plume', 'Ground Deformation']:
            if param.lower() in cell_text.lower():
                param_name = param
                break
    
    return param_name

def extract_cell_data(cell):
    """Extract data from table cell using unified logic"""
    # First try to find data in specific class element
    data_element = cell.find('p', class_='bold txtleft newfont')
    if data_element:
        return data_element.get_text(strip=True)
    
    # Try to find data in txt-no-eq class (for seismicity)
    txt_no_eq_element = cell.find('p', class_='txt-no-eq bold newfont')
    if txt_no_eq_element:
        return txt_no_eq_element.get_text(strip=True)
    
    # Try to get any text from the cell
    return cell.get_text(strip=True)

def extract_seismicity_with_description(cell):
    """Extract seismicity data with description from span tag"""
    # Look for the specific seismicity structure
    txt_no_eq_element = cell.find('p', class_='txt-no-eq bold newfont')
    if txt_no_eq_element:
        # Extract the numeric value (text before the span)
        full_text = txt_no_eq_element.get_text(strip=True)
        
        # Look for span with class containing 'txt-vq'
        span_element = txt_no_eq_element.find('span', class_=re.compile(r'txt-vq'))
        if span_element:
            # Get the description from the span
            description = span_element.get_text(strip=True)
            
            # Extract numeric value by removing the span text from full text
            numeric_part = full_text.replace(description, '').strip()
            
            # Ensure proper formatting: "numeric_value description"
            if numeric_part and description:
                return f"{numeric_part} {description}"
            elif numeric_part:
                return numeric_part
            else:
                return description
        else:
            # Fallback: try to extract number and remaining text with proper regex
            # Look for pattern: number + optional whitespace + text
            match = re.match(r'(\d+)\s*(.+)', full_text)
            if match:
                numeric_value = match.group(1)
                description = match.group(2).strip()
                # Format as requested: "numeric_value description"
                return f"{numeric_value} {description}"
            else:
                # If no number found, try to find just the number at the beginning
                number_match = re.match(r'(\d+)', full_text)
                if number_match:
                    numeric_value = number_match.group(1)
                    remaining_text = full_text[len(numeric_value):].strip()
                    if remaining_text:
                        return f"{numeric_value} {remaining_text}"
                    else:
                        return f"{numeric_value} volcanic earthquakes"  # Default description
    
    # Fallback to regular extraction with proper formatting
    cell_text = cell.get_text(strip=True)
    # Try to extract number and description from any cell text
    match = re.match(r'(\d+)\s*(.+)', cell_text)
    if match:
        numeric_value = match.group(1)
        description = match.group(2).strip()
        return f"{numeric_value} {description}"
    
    return cell_text

def map_parameter_to_key(param_name, data_value, focus_on_alert=False):
    """Map parameter name to result key with unified logic"""
    param_key = None
    param_lower = param_name.lower()
    
    if 'alert' in param_lower and 'level' in param_lower:
        param_key = 'Alert_Level'
        # Extract numeric value from alert level
        alert_match = re.search(r'(\d+)', data_value)
        if alert_match:
            return param_key, alert_match.group(1)
    elif 'eruption' in param_lower:
        param_key = 'Eruption'
    elif 'seismic' in param_lower:
        param_key = 'Seismicity'
        # For seismicity, return the full formatted string (number + description)
        return param_key, data_value
    elif 'acid' in param_lower:
        param_key = 'Acidity'
    elif 'temperature' in param_lower:
        param_key = 'Temperature'
    elif 'sulfur' in param_lower or 'dioxide' in param_lower:
        param_key = 'Sulfur_Dioxide_Flux'
        # Return the full text content including description and date
        # Instead of extracting just the numeric value, return the complete data
        return param_key, data_value
    elif 'plume' in param_lower:
        param_key = 'Plume'
    elif 'ground' in param_lower and 'deformation' in param_lower:
        param_key = 'Ground_Deformation'
    
    return param_key, data_value if param_key else (None, None)

def extract_from_all_tables(soup, missing_params):
    """Fallback method to search all tables for missing parameters"""
    result = {}
    
    try:
        tables = soup.find_all('table')
        for table in tables:
            # Check if this table contains parameter-related content
            table_text = table.get_text().lower()
            if any(param.lower().replace('_', ' ') in table_text for param in missing_params):
                extracted_data = parse_table_data(table)
                # Only update missing parameters
                for key, value in extracted_data.items():
                    if key in missing_params and value != '0':
                        result[key] = value
    
    except Exception as e:
        print(f"Error in fallback extraction: {str(e)}")
    
    return result

def extract_date_from_text(text):
    """Extract date from bulletin text"""
    if not text:
        return None
    
    # Look for date patterns in the text
    date_patterns = [
        r'(\d{1,2})\s+(January|February|March|April|May|June|July|August|September|October|November|December)\s+(\d{4})',
        r'(January|February|March|April|May|June|July|August|September|October|November|December)\s+(\d{1,2}),?\s+(\d{4})',
        r'(\d{4})-(\d{1,2})-(\d{1,2})',
        r'(\d{1,2})/(\d{1,2})/(\d{4})'
    ]
    
    for pattern in date_patterns:
        match = re.search(pattern, text, re.IGNORECASE)
        if match:
            return match.group(0)
    
    return None

def extract_date_from_url(url):
    """Extract date from URL if present"""
    if not url:
        return None
    
    # Look for date patterns in URL
    date_match = re.search(r'(\d{4})-(\d{1,2})-(\d{1,2})', url)
    if date_match:
        return date_match.group(0)
    
    return None

def extract_alert_level_from_row_one(soup):
    """Legacy wrapper for backward compatibility"""
    return extract_from_row_one_section(soup)

if __name__ == '__main__':
    app.run(debug=True)
