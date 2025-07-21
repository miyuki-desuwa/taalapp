from flask import Flask, request, jsonify, render_template
import requests
import re
from bs4 import BeautifulSoup
import json
from datetime import datetime

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

if __name__ == '__main__':
    app.run(debug=True)
